FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --no-create-home ledgersync
COPY --from=build /app/target/backend-*.jar app.jar
USER ledgersync
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xmx256m -Xss512k"
ENTRYPOINT ["java", "-jar", "app.jar"]
