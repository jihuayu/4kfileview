# 构建阶段
FROM maven:3.8-openjdk-8 AS builder

# 添加元数据
LABEL maintainer="jihuayu <jihuayu123@gmail.com>"
LABEL description="专注文件在线预览服务(fork by kekingcn/kkFileView)"
LABEL license="Apache-2.0"
LABEL url="https://github.com/jihuayu/kkFileView"

# 设置工作目录
WORKDIR /app

# 复制pom文件和源代码
COPY pom.xml .
COPY server/ server/

# 构建项目
RUN mvn -B package -Dmaven.test.skip=true --file pom.xml

# 运行阶段
FROM ubuntu:24.04

# 添加元数据
LABEL maintainer="jihuayu <jihuayu123@gmail.com>"
LABEL description="专注文件在线预览服务(fork by kekingcn/kkFileView)"
LABEL license="Apache-2.0"
LABEL url="https://github.com/jihuayu/kkFileView"

RUN apt-get update &&\
    export DEBIAN_FRONTEND=noninteractive &&\
	apt-get install -y --no-install-recommends openjdk-8-jre tzdata locales xfonts-utils fontconfig libreoffice-nogui &&\
    echo 'Asia/Shanghai' > /etc/timezone &&\
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime &&\
    localedef -i zh_CN -c -f UTF-8 -A /usr/share/locale/locale.alias zh_CN.UTF-8 &&\
    locale-gen zh_CN.UTF-8 &&\
    apt-get install -y --no-install-recommends ttf-mscorefonts-installer &&\
    apt-get install -y --no-install-recommends ttf-wqy-microhei ttf-wqy-zenhei xfonts-wqy &&\
	apt-get autoremove -y &&\
    apt-get clean &&\
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
