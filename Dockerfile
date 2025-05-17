# Use a lightweight JDK 21 base image (significantly smaller than Ubuntu)
FROM eclipse-temurin:21-jdk-alpine

# Set working directory
WORKDIR /datasuite/xml

# Copy jar file from build directory into new created directory
COPY build/libs/SN_XDataSuite-1.0.jar .

CMD ["java", "-jar", "SN_XDataSuite-1.0.jar"]