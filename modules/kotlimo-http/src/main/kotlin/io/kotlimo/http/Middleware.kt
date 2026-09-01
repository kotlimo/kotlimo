package io.kotlimo.http

fun interface Middleware {
    fun handle(request: Request, next: (Request) -> Response): Response
}

class MiddlewarePipeline(private val middleware: List<Middleware>) {
    fun send(request: Request, destination: (Request) -> Response): Response {
        if (middleware.isEmpty()) return destination(request)
        return io.kotlimo.pipeline.Pipeline<Request, Response>()
            .send(request)
            .through(middleware.map { pipe -> { req, next -> pipe.handle(req, next) } })
            .then(destination)
    }
}
