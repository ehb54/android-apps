# Build image for this repo: JDK 21, Gradle and the Android SDK command-line tools.
# tools/docker-build.sh builds it with PLATFORM and BUILD_TOOLS taken from
# gradle/libs.versions.toml. GitHub Actions uses the runner's own SDK instead.
# The "android" CLI (sdkmanager's replacement) reports usage to Google unless
# it gets --no-metrics, and installing through it accepts the SDK license.
FROM gradle:9.8.2-jdk21

ARG CMDLINE_TOOLS=16111833
ARG PLATFORM=android-37.0
ARG BUILD_TOOLS=36.0.0

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${PATH}

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl git unzip \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p ${ANDROID_HOME}/cmdline-tools \
 && cd ${ANDROID_HOME}/cmdline-tools \
 && curl -fsSLo tools.zip https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip \
 && unzip -q tools.zip \
 && rm tools.zip \
 && mv cmdline-tools latest \
 && android --no-metrics sdk install platform-tools "platforms/${PLATFORM}" "build-tools/${BUILD_TOOLS}" > /dev/null \
 && rm -rf /root/.android \
 && chmod -R a+rX ${ANDROID_HOME}
