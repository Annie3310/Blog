# 如何使用 SSE 像 ChatGPT 一样流式返回数据

ChatGPT 的流式传输并不是使用 Websocket 完成的, 而是使用了流式传输, 流式传输的方法很简单
只需要将如下的头写进响应中, 并且在数据没有全部写入之前都不要关闭连接, 每次写入数据后 flush 一次 writer

## 服务端代码如下
<details open>

<summary>SpringBoot</summary>

[代码文件](../src/main/kotlin/zone/annie/blogcontent/StreamController.kt)

```kotlin
@GetMapping("/stream")
fun stream(resp: HttpServletResponse) {
    val str = "Hello World"
    resp.setHeader("Content-Type", "text/event-stream")
    resp.setHeader("Cache-Control", "no-cache")
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
		header.Set("Cache-Control", "no-cache")
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

## 前端代码如下
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Stream</title>
</head>
<body>
<p id="content"></p>
</body>
<script>
    const contentElement = document.getElementById("content");
    // 创建 XMLHttpRequest 对象
    const xhr = new XMLHttpRequest();
    // 设置请求的 URL
    xhr.open(
            "GET",
            `http://localhost:8080/stream`
    );
    // 设置响应类型为 text/event-stream
    xhr.setRequestHeader("Content-Type", "text/event-stream");
    // 监听 readyStateChange 事件
    xhr.onreadystatechange = () => {
        // 如果 readyState 是 3，表示正在接收数据
        if (xhr.readyState === 3) {
            // 将数据添加到文本框中
            contentElement.textContent = xhr.responseText
        }
    };
    // 发送请求
    xhr.send();
</script>
</html>
```
# SSE 介绍
SSE 实际上就是基于流式传输, 它向客户端声明接下来的消息是流式的 (和下载一样), 从而达到由服务端向客户端发送消息的目的

SSE 每次发送消息都遵循如下格式
```
field: value\n
```
其中 field 可以取 4 个值 [[MDN]](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events#event_stream_format)
- data
  - 实际的数据
- event
  - 事件, 类似于 topic, 可自定义, 默认为 `message`
- id
  - 数据 id
  - 浏览器用lastEventId属性读取这个值。一旦连接断线，浏览器会发送一个 HTTP 头，里面包含一个特殊的Last-Event-ID头信息，将这个值发送回来，用来帮助服务器端重建连接。因此，这个头信息可以被视为一种同步机制。
- retry
  - 用于服务器告诉客户端该何时发起重试
  - `retry: 10000\n`

如果数据以 `:` 开头, 则表示该行为注释, 可以用于心跳
```
: comments
```

## 服务端的实现
<details open>

<summary>SpringBoot</summary>

[代码文件](../src/main/kotlin/zone/annie/blogcontent/StreamController.kt)

```kotlin
val ssePool = ConcurrentHashMap<String, SseEmitter>()

@GetMapping("/sse")
fun connectSse(clientId: String): SseEmitter {
    val emitter = SseEmitter()
    ssePool[clientId] = emitter

    emitter.onCompletion {
        println("on completion")
        ssePool.remove(clientId)
    }

    emitter.onError {
        println("on error")
        ssePool.remove(clientId)
    }

    emitter.onTimeout {
        println("on timeout")
        ssePool.remove(clientId)
    }

    return emitter
}

@PostMapping("/sse/send")
fun sendSse(@ModelAttribute clientId: String, @ModelAttribute message: String) {
    val emitter = ssePool[clientId] ?: throw IllegalStateException("未连接")
    emitter.send(message)
}
```

</details>

<details>

<summary>Go</summary>

[代码文件](../go/stream/stream_test.go)

Go SDK 中的 http 包中没有默认的 SSE 实现, 所以按格式返回就可以

Gin 对此做了一些简单的封装
```go
func serveGin() {
	e := gin.Default()
	e.GET("/", func(c *gin.Context) {
		str := "Hello, World!"
		c.Writer.Header().Set("Content-Type", "text/event-stream")
		c.Writer.Header().Set("Cache-Control", "no-cache")
		c.Writer.Header().Set("Connection", "keep-alive")

		for _, s := range str {
			for i := 0; i < 10; i++ {
				c.SSEvent("message", s)
				c.Writer.Flush()
				time.Sleep(1 * time.Second)
			}
		}
	})
	err := e.Run(":8080")
	if err != nil {
		return 
	}
}
```

</details>


## 前端实现
```js
const evtSource = new EventSource("//api.example.com/sse-demo.php", {
  withCredentials: true,
});

evtSource.onmessage = (event) => {
    const newElement = document.createElement("li");
    const eventList = document.getElementById("list");

    newElement.textContent = `message: ${event.data}`;
    eventList.appendChild(newElement);
};
```

### 监听自定义事件
```js
evtSource.addEventListener("ping", (event) => {
  const newElement = document.createElement("li");
  const eventList = document.getElementById("list");
  const time = JSON.parse(event.data).time;
  newElement.textContent = `ping at ${time}`;
  eventList.appendChild(newElement);
});
```

# 总结和对比 Websocket
- SSE 本质上是对 HTTP 的封装, Websocket 和 HTTP 同级
- SSE 是半双工, Websocket 是全双工
- SSE 更轻量级
- SSE 只可以传输文本, Websocket 同时可以传输二进制数据

# 参考
[Server-Sent Events 教程](https://www.ruanyifeng.com/blog/2017/05/server-sent_events.html)

[MDN](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)

