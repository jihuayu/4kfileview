# 构建阶段
FROM --platform=$BUILDPLATFORM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21 AS builder

# 设置工作目录
WORKDIR /app

# 复制pom文件和源代码
COPY pom.xml .
COPY server/ server/

# 构建项目；Docker 运行阶段安装系统 LibreOffice，无需准备 Windows Portable 包
RUN mvn -B package -Dmaven.test.skip=true -Dlibreoffice.portable.skip=true --file pom.xml

# 运行阶段
FROM --platform=$TARGETPLATFORM public.ecr.aws/docker/library/ubuntu:24.04

# 添加元数据
LABEL org.opencontainers.image.description="专注文件在线预览服务 | File preview service based on kkFileView"
LABEL org.opencontainers.image.source="https://github.com/jihuayu/kkFileView"
LABEL org.opencontainers.image.licenses="Apache-2.0"
LABEL maintainer="jihuayu <jihuayu123@gmail.com>"
RUN apt-get update && \
    export DEBIAN_FRONTEND=noninteractive && \
    # 预先接受微软字体 EULA
    echo "ttf-mscorefonts-installer msttcorefonts/accepted-mscorefonts-eula select true" | debconf-set-selections && \
    apt-get install -y --no-install-recommends \
        openjdk-21-jre \
        tzdata \
        locales \
        xfonts-utils \
        fontconfig \
        libreoffice-core \
        libreoffice-writer \
        libreoffice-calc \
        libreoffice-impress \
        libreoffice-common && \
    echo 'Asia/Shanghai' > /etc/timezone && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    localedef -i zh_CN -c -f UTF-8 -A /usr/share/locale/locale.alias zh_CN.UTF-8 && \
    locale-gen zh_CN.UTF-8 && \
    apt-get install -y --no-install-recommends ttf-mscorefonts-installer && \
    apt-get install -y --no-install-recommends \
        ttf-wqy-microhei \
        ttf-wqy-zenhei \
        xfonts-wqy && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 内置一些常用的中文字体，避免普遍性乱码
ADD docker/kkfileview-base/fonts/* /usr/share/fonts/chinese/

RUN cd /usr/share/fonts/chinese &&\
    # 安装字体
    mkfontscale &&\
    mkfontdir &&\
    fc-cache -fv

ENV LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8

# 设置工作目录
WORKDIR /opt

# 从构建阶段复制构建好的应用
COPY --from=builder /app/server/target/kkFileView-*.tar.gz .
RUN tar -xzf kkFileView-*.tar.gz && \
    mkdir -p kkFileView && \
    mv kkFileView-*/* kkFileView/ && \
    rm -rf kkFileView-* && \
    rm kkFileView-*.tar.gz || true

RUN mv /opt/kkFileView/bin/kkFileView-*.jar /opt/kkFileView/bin/kkFileView.jar

# 设置环境变量
ENV KKFILEVIEW_BIN_FOLDER=/opt/kkFileView/bin

# 声明端口
EXPOSE 8012

# 启动命令
ENTRYPOINT ["java","-Dfile.encoding=UTF-8","-Dspring.config.location=/opt/kkFileView/config/application.properties","-jar","/opt/kkFileView/bin/kkFileView.jar"]
