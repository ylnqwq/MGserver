# 微电网日优化调度业务层

本目录用于承载面向业务/API/网页后端的微电网优化调度代码，已从原论文实验脚本中剥离出来。当前版本只运行 `MOIABC`，不再执行多算法对比、统计检验或网页展示。

## 文件

- `microgrid_business.py`：业务入口，负责读取用户 CSV/XLSX 数据、构建微电网模型、调用 `MOIABC`、导出曲线数据。
- `input_template.csv`：24 小时输入模板，可直接给用户下载或作为前端上传格式参考。
- `results/`：默认输出目录。

## 输入格式

支持 `.csv`、`.xlsx`、`.xlsm`。推荐表头如下：

```csv
hour,buy_price,sell_price,load_kw,pv_kw,wt_kw
0,0.42,0.25,92,0,66
1,0.42,0.25,88,0,64
```

可选列：

- `buy_price` / `购电价` / `购电电价` / `电价`
- `sell_price` / `售电价` / `售电电价`
- `load_kw` / `负荷`
- `pv_kw` / `光伏`
- `wt_kw` / `wind_kw` / `风电`

数据长度支持 24 个小时点或 48 个半小时点。缺失列会使用原微电网案例默认曲线补齐；如果只上传单列价格，则该列会作为 `buy_price`，`sell_price` 会按默认售购电价比例自动生成。

## 命令行用法

生成输入模板：

```powershell
python MOIABC\microgrid_business.py --template MOIABC\input_template.csv
```

使用用户上传文件运行一次 MOIABC 日调度：

```powershell
python MOIABC\microgrid_business.py --input MOIABC\input_template.csv --output MOIABC\results
```

调小参数用于接口联调或快速测试：

```powershell
python MOIABC\microgrid_business.py --input MOIABC\input_template.csv --output MOIABC\results_dev --bee 20 --max-iter 50 --archive-size 30
```

## 输出

- `summary.json`：本次优化摘要，包括目标值、惩罚项、SOC、耗时和算法参数。
- `dispatch_curves.json` / `dispatch_curves.csv`：网页绘图所需的日调度曲线，包括负荷、风光、柴油机、储能、购售电、SOC 和电价。
- `pareto_front.json` / `pareto_front.csv`：Pareto 前沿点，含折中解标记。
- `convergence.json` / `convergence.csv`：MOIABC 收敛曲线。
- `result_all.json`：完整结果，包含折中解变量和原始调度数组。

## Python 调用

```python
from pathlib import Path
import sys

sys.path.insert(0, str(Path("MOIABC").resolve()))
from microgrid_business import MoiabcRunConfig, run_moiabc_dispatch, save_business_result

result = run_moiabc_dispatch(
    "MOIABC/input_template.csv",
    MoiabcRunConfig(bee=60, max_iter=300, archive_size=80),
)
save_business_result(result, Path("MOIABC/results"))
```
