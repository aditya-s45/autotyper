package main

import (
	"bufio"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sync"
	"syscall"
	"time"
)

var (
	tunnelMu       sync.RWMutex
	tunnelURL      string
	tunnelActive   bool
	tunnelProcess  *os.Process
	tunnelRegexp   = regexp.MustCompile(`https://[a-zA-Z0-9-]+\.trycloudflare\.com`)
	cloudflaredURL = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
)

// getTunnelURL returns the active public Cloudflare tunnel URL
func getTunnelURL() string {
	tunnelMu.RLock()
	defer tunnelMu.RUnlock()
	return tunnelURL
}

// isTunnelActive returns true if the tunnel is connected and public URL is available
func isTunnelActive() bool {
	tunnelMu.RLock()
	defer tunnelMu.RUnlock()
	return tunnelActive && tunnelURL != ""
}

// ensureCloudflaredBinary finds or downloads cloudflared.exe
func ensureCloudflaredBinary() (string, error) {
	// 1. Check if cloudflared is in system PATH
	if p, err := exec.LookPath("cloudflared"); err == nil {
		return p, nil
	}

	// Target location: %LOCALAPPDATA%\AutoTyper\bin\cloudflared.exe
	localAppData := os.Getenv("LOCALAPPDATA")
	if localAppData == "" {
		localAppData = os.Getenv("APPDATA")
	}
	targetDir := filepath.Join(localAppData, "AutoTyper", "bin")
	targetPath := filepath.Join(targetDir, "cloudflared.exe")

	if fi, err := os.Stat(targetPath); err == nil && fi.Size() > 10000000 {
		return targetPath, nil
	}

	// 2. Check if user's ScreenCapturer already has cloudflared downloaded
	appData := os.Getenv("APPDATA")
	if appData != "" {
		screenCapPath := filepath.Join(appData, "ScreenCapturer", "bin", "cloudflared.exe")
		if fi, err := os.Stat(screenCapPath); err == nil && fi.Size() > 10000000 {
			_ = os.MkdirAll(targetDir, 0755)
			if copyFile(screenCapPath, targetPath) == nil {
				return targetPath, nil
			}
			return screenCapPath, nil
		}
	}

	// 3. Download from Cloudflare GitHub releases
	_ = os.MkdirAll(targetDir, 0755)
	logMsg("🌐", "Downloading cloudflared for cellular / remote pairing (~30MB, one-time)...", ColorCyan)

	client := &http.Client{Timeout: 3 * time.Minute}
	resp, err := client.Get(cloudflaredURL)
	if err != nil {
		return "", fmt.Errorf("failed to download cloudflared: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("download returned status: %s", resp.Status)
	}

	tmpPath := targetPath + ".tmp"
	out, err := os.Create(tmpPath)
	if err != nil {
		return "", fmt.Errorf("failed to create temp file: %w", err)
	}

	_, err = io.Copy(out, resp.Body)
	out.Close()
	if err != nil {
		_ = os.Remove(tmpPath)
		return "", fmt.Errorf("failed writing cloudflared binary: %w", err)
	}

	_ = os.Remove(targetPath)
	if err := os.Rename(tmpPath, targetPath); err != nil {
		return "", fmt.Errorf("failed to finalize cloudflared binary: %w", err)
	}

	logMsg("✅", "cloudflared ready!", ColorGreen)
	return targetPath, nil
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()

	_, err = io.Copy(out, in)
	return err
}

// startCloudflareTunnel spawns cloudflared in background and extracts the public URL
func startCloudflareTunnel(localPort int) {
	binPath, err := ensureCloudflaredBinary()
	if err != nil {
		logMsg("⚠️ ", fmt.Sprintf("Cloudflare tunnel disabled: %v", err), ColorYellow)
		return
	}

	logMsg("🌐", "Starting Cloudflare Quick Tunnel...", ColorCyan)

	localTarget := fmt.Sprintf("http://127.0.0.1:%d", localPort)
	cmd := exec.Command(binPath, "tunnel", "--url", localTarget, "--no-autoupdate")
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow:    true,
		CreationFlags: 0x08000000, // CREATE_NO_WINDOW
	}

	stderr, err := cmd.StderrPipe()
	if err != nil {
		logMsg("⚠️ ", fmt.Sprintf("Cloudflare stderr pipe error: %v", err), ColorYellow)
		return
	}
	cmd.Stdout = io.Discard

	if err := cmd.Start(); err != nil {
		logMsg("⚠️ ", fmt.Sprintf("Failed to start cloudflared: %v", err), ColorYellow)
		return
	}

	tunnelMu.Lock()
	tunnelProcess = cmd.Process
	tunnelMu.Unlock()

	// Parse stderr in goroutine to detect the public trycloudflare.com URL
	go func() {
		hasCaptured := false
		scanner := bufio.NewScanner(stderr)
		for scanner.Scan() {
			line := scanner.Text()
			if !hasCaptured {
				if match := tunnelRegexp.FindString(line); match != "" {
					hasCaptured = true
					tunnelMu.Lock()
					tunnelURL = match
					tunnelActive = true
					tunnelMu.Unlock()

					logMsg("🚀", fmt.Sprintf("Cellular / Remote Tunnel ready: %s", match), ColorGreen)
					if isConsoleAttached {
						fmt.Printf("  🌐  %sCellular / 4G/5G Pairing: %s%s%s\n", ColorCyan, ColorBold, match, ColorEnd)
					}
				}
			}
		}

		// Wait for process completion or termination
		_ = cmd.Wait()

		tunnelMu.Lock()
		tunnelActive = false
		tunnelMu.Unlock()
	}()
}

// stopCloudflareTunnel cleanly terminates the tunnel subprocess
func stopCloudflareTunnel() {
	tunnelMu.Lock()
	defer tunnelMu.Unlock()

	if tunnelProcess != nil {
		_ = tunnelProcess.Kill()
		tunnelProcess = nil
		tunnelActive = false
	}
}
