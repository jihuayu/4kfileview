@echo off
set "FOURKFILEVIEW_BIN_FOLDER=%cd%"
cd "%FOURKFILEVIEW_BIN_FOLDER%"
set "JAR_NAME="
for %%F in (4kfileview-*.jar) do (
    set "JAR_NAME=%%~nxF"
    goto :jar_found
)
echo Error: 4kfileview jar not found in %FOURKFILEVIEW_BIN_FOLDER%
exit /b 1

:jar_found
echo Using FOURKFILEVIEW_BIN_FOLDER %FOURKFILEVIEW_BIN_FOLDER%
echo Using JAR_NAME %JAR_NAME%
echo Starting 4kfileview...
echo Please check log file in ../log/4kfileview.log for more information
echo Project repository: https://github.com/jihuayu/4kfileview
echo Issue tracker: https://github.com/jihuayu/4kfileview/issues
java -Dspring.config.location=..\config\application.properties -jar "%JAR_NAME%" > ..\log\4kfileview.log 2>&1
