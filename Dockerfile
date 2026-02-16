# ========================================
# STAGE 1: BUILD
# ========================================
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Copiar arquivos Gradle (cache de dependências)
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew ./

# Baixar dependências (layer com cache)
RUN ./gradlew dependencies --no-daemon || true

# Copiar código fonte
COPY src src

# Compilar aplicação
RUN ./gradlew bootJar --no-daemon

# ========================================
# STAGE 2: RUNTIME
# ========================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Criar usuário não-root (segurança)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiar JAR da stage de build
COPY --from=builder /app/build/libs/*.jar app.jar

# Copiar service account (se existir)
COPY --chown=spring:spring service-account-key.json /app/service-account-key.json

# Expor porta
EXPOSE 8081

# Variáveis de ambiente padrão
ENV JAVA_OPTS="-Xms256m -Xmx1024m" \
    SPRING_PROFILES_ACTIVE=prod

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# Comando de inicialização
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]