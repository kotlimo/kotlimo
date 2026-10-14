FROM eclipse-temurin:21-jdk
WORKDIR /src
COPY . .
RUN ./gradlew :website:installDist --no-daemon
EXPOSE 8000
CMD ["website/build/install/website/bin/website", "serve", "--host=0.0.0.0", "--port=8000"]
