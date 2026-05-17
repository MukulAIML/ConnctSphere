FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y rabbitmq-server python3
RUN echo 'import http.server; import socketserver; \n\
class Handler(http.server.SimpleHTTPRequestHandler):\n\
    def do_GET(self):\n\
        self.send_response(200)\n\
        self.send_header("Content-type", "application/json")\n\
        self.end_headers()\n\
        self.wfile.write(b"{\"version\":{\"number\":\"7.10.2\"}}")\n\
    def do_POST(self): self.do_GET()\n\
    def do_PUT(self): self.do_GET()\n\
httpd = socketserver.TCPServer(("", 9200), Handler)\n\
httpd.serve_forever()' > /mock_es.py
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT service rabbitmq-server start && python3 /mock_es.py & java -jar app.jar
