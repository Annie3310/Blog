package zone.annie.blogcontent

import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

@RestController
class StreamController {
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
}