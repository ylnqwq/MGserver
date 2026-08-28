# MGserver

微电网调度系统，包含 Spring Boot 后端、Vue 前端和 MOIABC 优化算法。

## 项目结构

- `backend/`: 后端 API 和任务调度服务
- `front/`: 前端页面
- `MOIABC/`: 算法脚本和输入模板
- `algorithm-runs/`: 任务运行目录，保存每次上传与输出结果
- `logs/`: 本地运行日志

`algorithm-runs/` 和 `logs/` 都是运行产物，通常不用手工维护。

## 环境要求

- JDK 21
- Maven
- Node.js 18+
- MySQL 8+
- Python 3

## 后端启动

后端默认端口是 `8081`，默认连接 MySQL：

```properties
MYSQL_URL=jdbc:mysql://localhost:3306/mgserver?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8
MYSQL_USER=root
MYSQL_PASSWORD=yln27878
```

启动：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

如果要改算法脚本位置或 Python 命令，可设置：

```properties
PYTHON_CMD=python
MG_ALGORITHM_DIR=E:\path\to\MOIABC
MOIABC_BEE=60
MOIABC_MAX_ITER=300
MOIABC_ARCHIVE_SIZE=80
```

## 前端启动

```powershell
cd front
npm install
npm run dev
```

前端开发服务器默认 `5173`，并将 `/api` 代理到 `http://localhost:8081`。

## 快速运行

- `start-mgserver.bat`: 直接启动打包好的后端 `MGserver-v1.0.0.jar`
- `submit-4-tasks.bat`: 一次提交 4 个示例调度任务

## 数据模板

下载模板接口：`GET /api/dispatch/template`

CSV 列名：

```csv
hour,buy_price,sell_price,load_kw,pv_kw,wt_kw
```

支持上传：`.csv`、`.xlsx`、`.xlsm`、`.xls`

要求：

- 共 24 行，对应 `hour=0..23`
- `hour` 必须按顺序填写
- 价格、负荷、光伏、风电都不能为负数

## 任务流程

1. 用户登录/注册
2. 上传预测文件和可选参数
3. 后端保存任务到 `algorithm-runs/task-{id}/`
4. 调用 `MOIABC/microgrid_business.py`
5. 读取 `summary.json`、`dispatch_curves.json`、`pareto_front.json` 等结果

## 常用接口

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/system/status`
- `GET /api/system/screenshot`
- `GET /api/settings`
- `PUT /api/settings`
- `GET /api/settings/algorithm`
- `PUT /api/settings/algorithm`
- `GET /api/dispatch/template`
- `POST /api/dispatch/preview`
- `POST /api/dispatch/tasks`
- `GET /api/dispatch/tasks`
- `GET /api/dispatch/tasks/{taskId}`
- `PUT /api/dispatch/tasks/{taskId}/name`
- `POST /api/dispatch/tasks/{taskId}/pause`
- `POST /api/dispatch/tasks/{taskId}/cancel`
- `DELETE /api/dispatch/tasks?ids=1,2,3`
- `GET /api/dispatch/tasks/{taskId}/download/{filename}`
- `GET /api/dispatch/admin/tasks`
- `GET /api/admin/users`
- `PUT /api/admin/users/{targetUserId}/role`

## 任务上传参数

`POST /api/dispatch/tasks` 需要：

- Header: `X-User-Id`
- `file`: 预测文件
- `name`: 任务名，可选
- `settings`: 设备参数 JSON，可选
- `algorithmSettings`: 算法参数 JSON，可选

## 可下载结果

任务完成后可下载：

- `dispatch_curves.csv`
- `dispatch_curves_balanced.csv`
- `dispatch_curves_economic_min.csv`
- `dispatch_curves_environment_min.csv`
- `pareto_front.csv`
- `convergence.csv`
- `report.pdf`

同时目录里还会生成：

- `summary.json`
- `dispatch_curves.json`
- `pareto_front.json`
- `solution_options.json`
- `convergence.json`

## 算法说明

MOIABC是独立的优化项目，具体可见https://github.com/ylnqwq/happyMMC

`MOIABC/microgrid_business.py` 是算法业务入口，支持单独运行，也支持由后端异步调用。

直接运行示例：

```powershell
python MOIABC\microgrid_business.py --input MOIABC\input_template.csv --output MOIABC\results
```