# 如何使用 SSE 像 ChatGPT 一样流式返回数据

ChatGPT 的流式传输并不是使用 WebSocket 完成的, 而是使用了流式传输, 流式传输的方法很简单
只需要将如下的头写进响应中, 并且在数据没有全部写入之前都不要关闭连接, 每次写入数据后 flush 一次 writer

## 服务端代码如下
<details open>

<summary>Kotlin/Java</summary>

[代码文件](../src/main/kotlin/zone/annie/blogcontent/StreamController.kt)

```kotlin
@GetMapping("/stream")
fun stream(resp: HttpServletResponse) {
    val str = "Hello World"
    resp.setHeader("Content-Type", "text/event-stream")
    resp.setHeader("Pragma", "no-cache")
    resp.contentType = "text/event-stream"
    resp.characterEncoding = "UTF-8"
    val out = resp.outputStream
    out.use {
        str.forEach {
            out.write(it.code)
            out.flush()
            Thread.sleep(100)
        }
    }
}
```

</details>

<details>

<summary>Go</summary>

[代码文件](../go/stream/stream_test.go)

```go
func serve() {
	mux := http.NewServeMux()
	mux.HandleFunc("/stream", func(w http.ResponseWriter, r *http.Request) {
		str := "Hello World!"

		header := w.Header()
		header.Set("Content-Type", "text/event-stream;charset=UTF-8")
		header.Set("Pragma", "no-cache")
		header.Set("Connection", "keep-alive")

		flusher := w.(http.Flusher)

		for _, s := range str {
			_, err := w.Write([]byte{byte(s)})
			if err != nil {
				panic(err)
			}
			flusher.Flush()
			time.Sleep(time.Millisecond * 100)
		}
	})
	
	// 注意跨域

	fmt.Println("Server running on http://localhost:8080")
	err := http.ListenAndServe(":8080", handler)
	if err != nil {
		panic(err)
	}
}
```

</details>