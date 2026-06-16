#!/bin/bash
#
#
#############################
# First_Author:  凯京科技
# Second_Author:  sanxi
# Version: 1.1
# Date:    2021/09/17
# Description:  v1.1：修改进程启动机制为pid形式。
#############################
#
DIR_HOME=("/opt/openoffice.org3" "/opt/libreoffice" "/opt/libreoffice6.1" "/opt/libreoffice7.0" "/opt/libreoffice7.1" "/opt/libreoffice7.2" "/opt/libreoffice7.3" "/opt/libreoffice7.4" "/opt/libreoffice7.5" "/opt/libreoffice7.6" "/opt/libreoffice24.2" "/opt/libreoffice24.8" "/opt/libreoffice25.2" "/opt/openoffice4" "/usr/lib/openoffice" "/usr/lib/libreoffice")
FLAG=
OFFICE_HOME=
FOURKFILEVIEW_BIN_FOLDER=$(cd "$(dirname "$0")" || exit 1 ;pwd)
PID_FILE_NAME="4kfileview.pid"
PID_FILE="${FOURKFILEVIEW_BIN_FOLDER}/${PID_FILE_NAME}"
export FOURKFILEVIEW_BIN_FOLDER=$FOURKFILEVIEW_BIN_FOLDER
#
## 如pid文件不存在则自动创建
if [ ! -f ${PID_FILE_NAME} ]; then
  touch "${FOURKFILEVIEW_BIN_FOLDER}/${PID_FILE_NAME}"
fi
## 判断当前是否有进程处于运行状态
if [ -s "${PID_FILE}" ]; then
  PID=$(cat "${PID_FILE}")
  echo "进程已处于运行状态，进程号为：${PID}"
  exit 1
else
  cd "$FOURKFILEVIEW_BIN_FOLDER" || exit 1
  echo "Using FOURKFILEVIEW_BIN_FOLDER $FOURKFILEVIEW_BIN_FOLDER"
  grep 'office\.home' ../config/application.properties | grep -v '^#' | grep -v 'default'
  if [ $? -eq 0 ]; then
    echo "Using customized office.home"
  else
  for i in ${DIR_HOME[@]}
    do
      if [ -f "$i/program/soffice.bin" ]; then
        FLAG=true
        OFFICE_HOME=${i}
        break
      fi
    done
    if [ ! -n "${FLAG}" ]; then
      echo "Installing LibreOffice"
      sh ./install.sh
    else
      echo "Detected office component has been installed in $OFFICE_HOME"
    fi
  fi

  JAR_PATH=$(ls 4kfileview-*.jar 2>/dev/null | head -n 1)
  if [ -z "${JAR_PATH}" ]; then
    echo "4kfileview jar not found in ${FOURKFILEVIEW_BIN_FOLDER}"
    exit 1
  fi

  ## 启动4kfileview
  echo "Starting 4kfileview..."
  echo "Using jar ${JAR_PATH}"
  nohup java -Dfile.encoding=UTF-8 -Dspring.config.location=../config/application.properties -jar "${JAR_PATH}" > ../log/4kfileview.log 2>&1 &
  echo "Please execute ./showlog.sh to check log for more information"
  echo "Project repository: https://github.com/jihuayu/4kfileview"
  echo "Issue tracker: https://github.com/jihuayu/4kfileview/issues"
  PROCESS=$(ps -ef | grep -v grep | grep java | grep 4kfileview | awk 'NR==1{print $2}')
  # 启动成功后将进程号写入pid文件
  echo "$PROCESS" > "$PID_FILE"
fi
