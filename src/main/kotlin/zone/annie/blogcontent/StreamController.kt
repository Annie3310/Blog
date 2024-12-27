package zone.annie.blogcontent

import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StreamController {
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
}