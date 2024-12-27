# 如何使用 SSE 像 ChatGPT 一样流式返回数据

ChatGPT 的流式传输并不是使用 WebSocket 完成的, 而是使用了流式传输, 流式传输的方法很简单
只需要将如下的头写进响应中, 并且在数据没有全部写入之前都不要关闭连接, 每次写入数据后 flush 一次 writer
<details open>
<summary>Kotlin/Java</summary>
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
</details>