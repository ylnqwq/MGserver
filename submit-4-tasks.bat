@echo off
setlocal

set "BASE_URL=http://127.0.0.1:8081"
set "USER_ID=1"
set "TMP_CSV=%TEMP%\mgserver_submit_template.csv"

> "%TMP_CSV%" (
  echo hour,buy_price,sell_price,load_kw,pv_kw,wt_kw
  echo 0,0.42,0.25,42,0,32
  echo 1,0.42,0.25,39,0,31
  echo 2,0.42,0.25,34,0,33
  echo 3,0.42,0.25,33,0,35
  echo 4,0.42,0.25,35,0,37
  echo 5,0.42,0.25,42,3,38
  echo 6,0.52,0.32,48,5,36
  echo 7,0.52,0.32,52,12,35
  echo 8,0.52,0.32,54,24,34
  echo 9,0.60,0.38,60,36,32
  echo 10,0.60,0.38,58,44,30
  echo 11,0.60,0.38,56,50,28
  echo 12,0.60,0.38,55,52,26
  echo 13,0.60,0.38,58,46,24
  echo 14,0.60,0.38,62,36,22
  echo 15,0.52,0.32,68,24,20
  echo 16,0.52,0.32,78,12,22
  echo 17,0.60,0.38,88,5,25
  echo 18,0.60,0.38,92,0,28
  echo 19,0.60,0.38,88,0,32
  echo 20,0.52,0.32,76,0,34
  echo 21,0.52,0.32,64,0,36
  echo 22,0.52,0.32,54,0,34
  echo 23,0.42,0.25,46,0,33
)

for %%N in (1 2 3 4) do (
  echo Submitting task %%N...
  curl -s -X POST "%BASE_URL%/api/dispatch/tasks" ^
    -H "X-User-Id: %USER_ID%" ^
    -F "name=task-%%N" ^
    -F "file=@%TMP_CSV%"
  echo.
)

del "%TMP_CSV%" >nul 2>nul
echo Done.
