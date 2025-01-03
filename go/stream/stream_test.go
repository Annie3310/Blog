package stream

import (
	"fmt"
	"github.com/gin-gonic/gin"
	"net/http"
	"testing"
	"time"
)

func TestStream(t *testing.T) {
	serve()
}

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
	// 使用 CORS 中间件
	handler := corsMiddleware(mux)

	fmt.Println("Server running on http://localhost:8080")
	err := http.ListenAndServe(":8080", handler)
	if err != nil {
		panic(err)
	}
}

// 跨域中间件
func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// 设置 CORS 头
		w.Header().Set("Access-Control-Allow-Origin", "*")                                // 允许所有来源
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS") // 允许的方法
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")     // 允许的头

		// 处理预检请求
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}

		next.ServeHTTP(w, r)
	})
}
