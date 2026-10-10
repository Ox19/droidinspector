# Entorno de build aislado: Gradle nunca corre sobre macOS.
# amd64 porque AGP solo publica aapt2 de Linux para x86_64; en Apple Silicon corre emulado.
FROM --platform=linux/amd64 eclipse-temurin:25-jdk@sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc

ARG CMDLINE_TOOLS_ZIP=commandlinetools-linux-16111833_latest.zip
# SHA-1 publicado por Google en repository2-3.xml (único checksum que ofrece)
ARG CMDLINE_TOOLS_SHA1=e025545c62a8e64c7559119566a569fb1dec5f60
ENV ANDROID_HOME=/opt/android-sdk

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl unzip \
 && rm -rf /var/lib/apt/lists/* \
 && (userdel --remove ubuntu 2>/dev/null || true) \
 && useradd --create-home --uid 1000 builder \
 && mkdir -p "$ANDROID_HOME" /workspace /home/builder/.gradle \
 && chown builder:builder "$ANDROID_HOME" /workspace /home/builder/.gradle

USER builder
WORKDIR /workspace

RUN curl -fsSLo /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}" \
 && echo "${CMDLINE_TOOLS_SHA1}  /tmp/cmdline-tools.zip" | sha1sum -c - \
 && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
 && mkdir -p "$ANDROID_HOME/cmdline-tools" \
 && mv /tmp/cmdline-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest" \
 && rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip

# Versiones que pide AGP 9.4.1. cmdline-tools 23 usa "/" y acepta la licencia al instalar.
# Si AGP pide otra, la descarga en la fase con red (la licencia ya está en el SDK).
RUN yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
      "platforms/android-37.0" "build-tools/36.0.0"
