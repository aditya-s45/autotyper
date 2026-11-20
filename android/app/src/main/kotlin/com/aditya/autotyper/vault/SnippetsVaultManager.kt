package com.aditya.autotyper.vault

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class VaultSnippet(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val language: String,
    val tags: List<String>,
    val code: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

class SnippetsVaultManager(private val context: Context) {
    private val vaultFile = File(context.filesDir, "snippets_vault.json")
    private val _snippets = MutableStateFlow<List<VaultSnippet>>(emptyList())
    val snippets: StateFlow<List<VaultSnippet>> = _snippets.asStateFlow()

    init {
        loadSnippets()
    }

    private fun loadSnippets() {
        if (!vaultFile.exists()) {
            val defaults = getDefaultSnippets()
            saveToFile(defaults)
            _snippets.value = defaults
            return
        }

        try {
            val text = vaultFile.readText()
            val jsonArr = JSONArray(text)
            val list = mutableListOf<VaultSnippet>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
                val tagsList = mutableListOf<String>()
                for (j in 0 until tagsArr.length()) {
                    tagsList.add(tagsArr.getString(j))
                }

                list.add(
                    VaultSnippet(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "Untitled"),
                        language = obj.optString("language", "Text"),
                        tags = tagsList,
                        code = obj.optString("code", ""),
                        isFavorite = obj.optBoolean("is_favorite", false),
                        createdAt = obj.optLong("created_at", System.currentTimeMillis())
                    )
                )
            }
            _snippets.value = list
        } catch (e: Exception) {
            val defaults = getDefaultSnippets()
            _snippets.value = defaults
        }
    }

    private fun saveToFile(list: List<VaultSnippet>) {
        try {
            val jsonArr = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("language", item.language)
                    put("code", item.code)
                    put("is_favorite", item.isFavorite)
                    put("created_at", item.createdAt)
                    val tagsArr = JSONArray()
                    item.tags.forEach { tagsArr.put(it) }
                    put("tags", tagsArr)
                }
                jsonArr.put(obj)
            }
            vaultFile.writeText(jsonArr.toString(2))
        } catch (_: Exception) {}
    }

    fun addSnippet(title: String, language: String, tags: List<String>, code: String): VaultSnippet {
        val snippet = VaultSnippet(
            title = title.trim(),
            language = language.trim(),
            tags = tags.map { if (it.startsWith("#")) it else "#$it" },
            code = code
        )
        val updated = listOf(snippet) + _snippets.value
        _snippets.value = updated
        saveToFile(updated)
        return snippet
    }

    fun updateSnippet(updatedSnippet: VaultSnippet) {
        val updated = _snippets.value.map {
            if (it.id == updatedSnippet.id) updatedSnippet else it
        }
        _snippets.value = updated
        saveToFile(updated)
    }

    fun deleteSnippet(id: String) {
        val updated = _snippets.value.filter { it.id != id }
        _snippets.value = updated
        saveToFile(updated)
    }

    fun toggleFavorite(id: String) {
        val updated = _snippets.value.map {
            if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it
        }
        _snippets.value = updated
        saveToFile(updated)
    }

    fun resetToDefaults() {
        val defaults = getDefaultSnippets()
        _snippets.value = defaults
        saveToFile(defaults)
    }

    private fun getDefaultSnippets(): List<VaultSnippet> {
        return listOf(
            VaultSnippet(
                title = "C++ CP Fast I/O Starter",
                language = "C++",
                tags = listOf("#cp", "#boilerplate", "#algorithms"),
                code = """
#include <bits/stdc++.h>
using namespace std;

void solve() {
    int n;
    if (!(cin >> n)) return;
    vector<int> a(n);
    for (int &x : a) cin >> x;
    cout << "Processed: " << n << "\n";
}

int main() {
    ios_base::sync_with_stdio(false);
    cin.tie(NULL);
    int t = 1;
    if (cin >> t) {
        while (t--) solve();
    }
    return 0;
}
                """.trimIndent(),
                isFavorite = true
            ),
            VaultSnippet(
                title = "Python Fast I/O & Recursion Template",
                language = "Python",
                tags = listOf("#cp", "#python", "#interview"),
                code = """
import sys
sys.setrecursionlimit(200005)

def solve():
    input_data = sys.stdin.read().split()
    if not input_data:
        return
    n = int(input_data[0])
    nums = [int(x) for x in input_data[1:n+1]]
    print(f"Read {len(nums)} elements")

if __name__ == '__main__':
    solve()
                """.trimIndent(),
                isFavorite = true
            ),
            VaultSnippet(
                title = "Binary Search (Safe Bounds)",
                language = "C++",
                tags = listOf("#algorithms", "#cp", "#interview"),
                code = """
int binarySearch(const vector<int>& arr, int target) {
    int low = 0, high = (int)arr.size() - 1;
    while (low <= high) {
        int mid = low + (high - low) / 2;
        if (arr[mid] == target) return mid;
        if (arr[mid] < target) low = mid + 1;
        else high = mid - 1;
    }
    return -1;
}
                """.trimIndent(),
                isFavorite = false
            ),
            VaultSnippet(
                title = "Breadth-First Search (BFS)",
                language = "Python",
                tags = listOf("#graphs", "#bfs", "#python"),
                code = """
from collections import deque

def bfs(graph, start):
    visited = {start}
    queue = deque([start])
    order = []
    while queue:
        node = queue.popleft()
        order.append(node)
        for neighbor in graph.get(node, []):
            if neighbor not in visited:
                visited.add(neighbor)
                queue.append(neighbor)
    return order
                """.trimIndent(),
                isFavorite = false
            ),
            VaultSnippet(
                title = "Dijkstra's Shortest Path",
                language = "C++",
                tags = listOf("#graphs", "#dijkstra", "#cp"),
                code = """
vector<long long> dijkstra(int n, int src, const vector<vector<pair<int, int>>>& adj) {
    const long long INF = 1e18;
    vector<long long> dist(n + 1, INF);
    priority_queue<pair<long long, int>, vector<pair<long long, int>>, greater<>> pq;
    dist[src] = 0;
    pq.push({0, src});
    while (!pq.empty()) {
        auto [d, u] = pq.top();
        pq.pop();
        if (d > dist[u]) continue;
        for (auto [v, w] : adj[u]) {
            if (dist[u] + w < dist[v]) {
                dist[v] = dist[u] + w;
                pq.push({dist[v], v});
            }
        }
    }
    return dist;
}
                """.trimIndent(),
                isFavorite = true
            ),
            VaultSnippet(
                title = "Fast Modulo Exponentiation",
                language = "Python",
                tags = listOf("#math", "#cp", "#python"),
                code = """
def power_mod(base, exp, mod):
    res = 1
    base %= mod
    while exp > 0:
        if exp % 2 == 1:
            res = (res * base) % mod
        base = (base * base) % mod
        exp //= 2
    return res
                """.trimIndent(),
                isFavorite = false
            ),
            VaultSnippet(
                title = "SQL Window Aggregates",
                language = "SQL",
                tags = listOf("#sql", "#database", "#interview"),
                code = """
SELECT 
    user_id,
    order_date,
    amount,
    ROW_NUMBER() OVER(PARTITION BY user_id ORDER BY order_date DESC) AS rn,
    SUM(amount) OVER(PARTITION BY user_id) AS total_user_spent,
    AVG(amount) OVER(PARTITION BY user_id) AS avg_user_spent
FROM orders
WHERE status = 'completed';
                """.trimIndent(),
                isFavorite = false
            ),
            VaultSnippet(
                title = "Docker Multi-Stage Build",
                language = "Docker",
                tags = listOf("#docker", "#devops"),
                code = """
# Build Stage
FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /app/server .

# Production Minimal Stage
FROM alpine:latest
RUN apk --no-cache add ca-certificates
WORKDIR /root/
COPY --from=builder /app/server .
EXPOSE 8080
CMD ["./server"]
                """.trimIndent(),
                isFavorite = false
            ),
            VaultSnippet(
                title = "Bash Safe Shell Script Boilerplate",
                language = "Bash",
                tags = listOf("#bash", "#linux", "#devops"),
                code = """
#!/usr/bin/env bash
set -Eeuo pipefail
trap 'echo "Error on line ${'$'}{LINENO}"' ERR

main() {
    echo "Running secure task..."
}

main "${'$'}@"
                """.trimIndent(),
                isFavorite = false
            )
        )
    }
}
