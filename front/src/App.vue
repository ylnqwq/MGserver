<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  Activity,
  CalendarDays,
  CheckCircle2,
  CircleUserRound,
  CloudUpload,
  Cpu,
  Download,
  FileSpreadsheet,
  FileText,
  Gauge,
  HardDrive,
  History,
  LayoutDashboard,
  LogOut,
  Monitor,
  Play,
  RefreshCw,
  Settings,
  ShieldCheck,
  Sun,
  UploadCloud,
  Wind,
  X,
  Zap
} from '@lucide/vue'

const apiBase = import.meta.env.VITE_API_BASE || ''
const userStorageKey = 'mg_user'
const recentConfigKey = 'mg_recent_dispatch_config'

const activeModule = ref('overview')
const sidebarOpen = ref(false)
const user = ref(readStoredUser())
const authMode = ref('login')
const authRole = ref('USER')
const captchaText = ref('')
const loginTransition = ref(false)
const loginTransitionReason = ref('')
const authForm = reactive({ username: '', password: '', captcha: '' })
const message = ref('')
const tooltip = reactive({ show: false, x: 0, y: 0, title: '', lines: [] })
const activeChartHover = reactive({ chart: '', index: null })
const loading = ref(false)
const refreshingCurrent = ref(false)
const previewLoading = ref(false)
const submittingTask = ref(false)
const selectedTask = ref(null)
const latestCompletedTaskDetail = ref(null)
const selectedOptionKey = ref('balanced')
const selectedTaskIds = ref([])
const uploadValidationErrors = ref([])
const fileInputRef = ref(null)
const serverStatus = ref(null)
const serverOnline = ref(false)
const serverStatusFetchedAt = ref('')
const serverMetricHistory = ref([])
const desktopScreenshotUrl = ref('')
const desktopScreenshotLoading = ref(false)
const desktopScreenshotError = ref('')
const desktopScreenshotExpanded = ref(false)
const desktopScreenshotPreviewOpen = ref(false)
const tasks = ref([])
const adminUsers = ref([])
const pollingTaskId = ref(null)
const previousParamSet = ref('system-default')
const recentConfig = ref(readRecentConfig())
const showConfirm = ref(false)
let pollTimer = null
let statusTimer = null
let pollingRequestActive = false
let messageTimer = null
let helpTooltipTimer = null

const uploadForm = reactive({
  name: defaultTaskName(),
  file: null
})

function rowsFromTuples(rows) {
  return rows.map(([hour, buyPrice, sellPrice, load, pv, wind]) => ({ hour, buyPrice, sellPrice, load, pv, wind }))
}

const defaultProfileRows = rowsFromTuples([
  [0, 0.42, 0.25, 92, 0, 66],
  [1, 0.42, 0.25, 88, 0, 64],
  [2, 0.42, 0.25, 80, 0, 68],
  [3, 0.42, 0.25, 76, 0, 70],
  [4, 0.42, 0.25, 78, 0, 72],
  [5, 0.42, 0.25, 90, 8, 76],
  [6, 0.52, 0.32, 108, 18, 74],
  [7, 0.52, 0.32, 126, 42, 70],
  [8, 0.52, 0.32, 136, 72, 66],
  [9, 0.60, 0.38, 148, 98, 62],
  [10, 0.60, 0.38, 142, 112, 58],
  [11, 0.60, 0.38, 138, 120, 54],
  [12, 0.60, 0.38, 134, 118, 50],
  [13, 0.60, 0.38, 140, 106, 48],
  [14, 0.60, 0.38, 148, 84, 46],
  [15, 0.52, 0.32, 160, 56, 44],
  [16, 0.52, 0.32, 176, 28, 48],
  [17, 0.60, 0.38, 196, 10, 56],
  [18, 0.60, 0.38, 205, 0, 62],
  [19, 0.60, 0.38, 198, 0, 70],
  [20, 0.52, 0.32, 174, 0, 76],
  [21, 0.52, 0.32, 150, 0, 78],
  [22, 0.52, 0.32, 126, 0, 74],
  [23, 0.42, 0.25, 104, 0, 70]
])

const dispatchScenarios = [
  {
    key: 'template',
    name: '系统模板',
    filename: 'microgrid_dispatch_template.csv',
    tag: '默认',
    detail: '标准日负荷与新能源预测',
    rows: defaultProfileRows
  },
  {
    key: 'sunny-high-pv',
    name: '晴天高光伏',
    filename: 'scenario_sunny_high_pv.csv',
    tag: '高光伏',
    detail: '午间光伏充足，适合观察储能充电与上网',
    rows: rowsFromTuples([
      [0, 0.40, 0.24, 82, 0, 42], [1, 0.40, 0.24, 78, 0, 40], [2, 0.40, 0.24, 74, 0, 38], [3, 0.40, 0.24, 72, 0, 37],
      [4, 0.40, 0.24, 76, 0, 38], [5, 0.42, 0.25, 86, 10, 40], [6, 0.50, 0.31, 102, 34, 42], [7, 0.50, 0.31, 118, 68, 40],
      [8, 0.54, 0.34, 130, 105, 38], [9, 0.58, 0.37, 138, 142, 36], [10, 0.58, 0.37, 136, 168, 34], [11, 0.58, 0.37, 132, 182, 32],
      [12, 0.58, 0.37, 130, 188, 30], [13, 0.58, 0.37, 136, 174, 32], [14, 0.58, 0.37, 146, 148, 34], [15, 0.54, 0.34, 158, 108, 36],
      [16, 0.54, 0.34, 174, 62, 38], [17, 0.62, 0.40, 198, 24, 42], [18, 0.66, 0.42, 214, 4, 48], [19, 0.66, 0.42, 206, 0, 52],
      [20, 0.56, 0.35, 176, 0, 54], [21, 0.50, 0.31, 148, 0, 50], [22, 0.42, 0.25, 120, 0, 46], [23, 0.40, 0.24, 96, 0, 44]
    ])
  },
  {
    key: 'cloudy-low-pv',
    name: '阴天低光伏',
    filename: 'scenario_cloudy_low_pv.csv',
    tag: '低光伏',
    detail: '光伏出力受限，主网和燃机承担更多缺口',
    rows: rowsFromTuples([
      [0, 0.42, 0.25, 88, 0, 60], [1, 0.42, 0.25, 84, 0, 58], [2, 0.42, 0.25, 80, 0, 56], [3, 0.42, 0.25, 78, 0, 54],
      [4, 0.42, 0.25, 82, 0, 55], [5, 0.44, 0.26, 94, 2, 58], [6, 0.54, 0.33, 116, 6, 62], [7, 0.54, 0.33, 136, 12, 60],
      [8, 0.58, 0.36, 150, 20, 58], [9, 0.62, 0.39, 160, 28, 56], [10, 0.62, 0.39, 156, 34, 54], [11, 0.62, 0.39, 152, 38, 52],
      [12, 0.62, 0.39, 150, 40, 50], [13, 0.62, 0.39, 154, 36, 50], [14, 0.62, 0.39, 166, 28, 52], [15, 0.56, 0.35, 178, 20, 54],
      [16, 0.56, 0.35, 194, 10, 58], [17, 0.66, 0.42, 220, 2, 62], [18, 0.70, 0.44, 238, 0, 68], [19, 0.70, 0.44, 226, 0, 72],
      [20, 0.58, 0.36, 194, 0, 74], [21, 0.54, 0.33, 164, 0, 70], [22, 0.44, 0.26, 132, 0, 66], [23, 0.42, 0.25, 104, 0, 62]
    ])
  },
  {
    key: 'windy-low-load',
    name: '大风低负荷',
    filename: 'scenario_windy_low_load.csv',
    tag: '高风电',
    detail: '夜间风电富余，容易出现充电和售电',
    rows: rowsFromTuples([
      [0, 0.38, 0.23, 58, 0, 112], [1, 0.38, 0.23, 54, 0, 118], [2, 0.38, 0.23, 50, 0, 120], [3, 0.38, 0.23, 48, 0, 116],
      [4, 0.38, 0.23, 52, 0, 110], [5, 0.40, 0.24, 62, 4, 104], [6, 0.48, 0.30, 76, 12, 98], [7, 0.48, 0.30, 88, 26, 92],
      [8, 0.52, 0.32, 96, 48, 88], [9, 0.56, 0.35, 104, 70, 82], [10, 0.56, 0.35, 100, 82, 78], [11, 0.56, 0.35, 98, 88, 74],
      [12, 0.56, 0.35, 96, 86, 72], [13, 0.56, 0.35, 100, 76, 76], [14, 0.56, 0.35, 108, 58, 82], [15, 0.52, 0.32, 122, 36, 90],
      [16, 0.52, 0.32, 138, 18, 96], [17, 0.62, 0.39, 156, 6, 102], [18, 0.64, 0.41, 166, 0, 108], [19, 0.64, 0.41, 158, 0, 114],
      [20, 0.52, 0.32, 132, 0, 118], [21, 0.48, 0.30, 104, 0, 116], [22, 0.40, 0.24, 82, 0, 112], [23, 0.38, 0.23, 66, 0, 108]
    ])
  },
  {
    key: 'evening-peak',
    name: '晚高峰',
    filename: 'scenario_evening_peak.csv',
    tag: '高负荷',
    detail: '17-20 点负荷抬升，验证储能削峰能力',
    rows: rowsFromTuples([
      [0, 0.40, 0.24, 86, 0, 48], [1, 0.40, 0.24, 82, 0, 46], [2, 0.40, 0.24, 78, 0, 44], [3, 0.40, 0.24, 76, 0, 44],
      [4, 0.40, 0.24, 82, 0, 46], [5, 0.42, 0.25, 96, 6, 48], [6, 0.50, 0.31, 118, 16, 50], [7, 0.50, 0.31, 138, 38, 48],
      [8, 0.54, 0.34, 148, 66, 46], [9, 0.58, 0.36, 154, 92, 44], [10, 0.58, 0.36, 150, 108, 42], [11, 0.58, 0.36, 146, 116, 40],
      [12, 0.58, 0.36, 144, 112, 40], [13, 0.58, 0.36, 150, 96, 42], [14, 0.58, 0.36, 166, 70, 44], [15, 0.56, 0.35, 190, 44, 46],
      [16, 0.62, 0.39, 226, 20, 50], [17, 0.74, 0.47, 268, 6, 56], [18, 0.78, 0.50, 292, 0, 64], [19, 0.78, 0.50, 278, 0, 70],
      [20, 0.66, 0.42, 232, 0, 74], [21, 0.54, 0.33, 180, 0, 70], [22, 0.44, 0.27, 136, 0, 62], [23, 0.40, 0.24, 104, 0, 54]
    ])
  },
  {
    key: 'extreme-load',
    name: '极端负荷',
    filename: 'scenario_extreme_load.csv',
    tag: '压力',
    detail: '全天负荷偏高，用于压力测试购电和燃机约束',
    rows: rowsFromTuples([
      [0, 0.46, 0.28, 126, 0, 42], [1, 0.46, 0.28, 120, 0, 40], [2, 0.46, 0.28, 116, 0, 38], [3, 0.46, 0.28, 112, 0, 36],
      [4, 0.46, 0.28, 120, 0, 38], [5, 0.50, 0.30, 146, 4, 42], [6, 0.62, 0.38, 184, 12, 46], [7, 0.62, 0.38, 218, 28, 48],
      [8, 0.66, 0.42, 238, 52, 46], [9, 0.70, 0.45, 256, 74, 44], [10, 0.70, 0.45, 248, 88, 42], [11, 0.70, 0.45, 242, 94, 40],
      [12, 0.70, 0.45, 240, 90, 38], [13, 0.70, 0.45, 250, 78, 40], [14, 0.70, 0.45, 270, 58, 42], [15, 0.66, 0.42, 296, 34, 46],
      [16, 0.70, 0.45, 326, 14, 52], [17, 0.82, 0.54, 358, 2, 58], [18, 0.86, 0.56, 372, 0, 64], [19, 0.86, 0.56, 354, 0, 68],
      [20, 0.74, 0.48, 306, 0, 70], [21, 0.62, 0.38, 248, 0, 64], [22, 0.50, 0.30, 190, 0, 54], [23, 0.46, 0.28, 146, 0, 48]
    ])
  }
]

const importedProfileRows = ref([])
const selectedScenarioKey = ref('template')

const taskSettings = reactive({
  microTurbineMinKw: 20,
  microTurbineMaxKw: 160,
  microTurbineRampUpKw: 55,
  microTurbineRampDownKw: 55,
  microTurbineUnitCost: 0.78,
  batteryCapacityKwh: 360,
  batteryChargeMaxKw: 90,
  batteryDischargeMaxKw: 90,
  batterySocMin: 0.2,
  batterySocMax: 0.9,
  batterySocInitial: 0.5,
  gridBuyMaxKw: 240,
  gridSellMaxKw: 160,
  renewableCurtailmentCost: 0.1
})

const defaultSettings = reactive({ ...taskSettings })
const algorithmSettings = reactive({
  bee: 60,
  maxIter: 300,
  limit: 120,
  archiveSize: 80,
  tournamentSize: 3,
  eliteRate: 0.25,
  eliminationRate: 0.25,
  archiveGuidanceRate: 0.4
})
const taskAlgorithmSettings = reactive({ ...algorithmSettings })
const showTaskAlgorithmOptions = ref(false)

const settingFields = [
  ['microTurbineMinKw', '燃气轮机出力下限 kW'],
  ['microTurbineMaxKw', '燃气轮机出力上限 kW'],
  ['microTurbineRampUpKw', '燃气轮机爬坡上限 kW/h'],
  ['microTurbineRampDownKw', '燃气轮机降坡上限 kW/h'],
  ['microTurbineUnitCost', '燃气轮机单位成本'],
  ['batteryCapacityKwh', '储能容量 kWh'],
  ['batteryChargeMaxKw', '储能充电上限 kW'],
  ['batteryDischargeMaxKw', '储能放电上限 kW'],
  ['batterySocMin', 'SOC 下限'],
  ['batterySocMax', 'SOC 上限'],
  ['batterySocInitial', '初始 SOC'],
  ['gridBuyMaxKw', '主网购电上限 kW'],
  ['gridSellMaxKw', '主网售电上限 kW'],
  ['renewableCurtailmentCost', '弃风弃光惩罚成本']
]
const algorithmSettingFields = [
  ['bee', '蜂群规模 bee'],
  ['maxIter', '最大迭代次数 maxIter'],
  ['limit', '局部搜索限制 limit'],
  ['archiveSize', '外部档案规模 archiveSize'],
  ['tournamentSize', '锦标赛规模 tournamentSize'],
  ['eliteRate', '精英保留率 eliteRate'],
  ['eliminationRate', '淘汰率 eliminationRate'],
  ['archiveGuidanceRate', '档案引导率 archiveGuidanceRate']
]
const algorithmIntegerKeys = new Set(['bee', 'maxIter', 'limit', 'archiveSize', 'tournamentSize'])

const isAdmin = computed(() => user.value?.role === 'ADMIN')
const modules = computed(() => [
  { key: 'overview', label: '调度总览', icon: LayoutDashboard },
  { key: 'server', label: '服务器运行状态', icon: Gauge },
  { key: 'planner', label: '计划与资源', icon: CalendarDays },
  { key: 'history', label: '运行历史', icon: History },
  ...(isAdmin.value ? [{ key: 'admin', label: '权限与参数', icon: ShieldCheck }] : [])
])
const hasImportedProfile = computed(() => importedProfileRows.value.length > 0)
const activeProfileRows = computed(() => hasImportedProfile.value ? importedProfileRows.value : defaultProfileRows)
const activeScenario = computed(() => dispatchScenarios.find((item) => item.key === selectedScenarioKey.value) || dispatchScenarios[0])
const selectedDataFileName = computed(() => uploadForm.file?.name || activeScenario.value?.filename || 'microgrid_dispatch_template.csv')
const latestCompletedTask = computed(() => {
  return [...tasks.value]
    .filter((task) => task.status === 'COMPLETED')
    .sort((a, b) => {
      const timeDiff = new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
      return timeDiff || n(b.id) - n(a.id)
    })[0] || null
})
const chartTask = computed(() => selectedTask.value || latestCompletedTaskDetail.value)

const solutionOptions = computed(() => Array.isArray(chartTask.value?.solutionOptions) ? chartTask.value.solutionOptions : [])
const paretoOptions = computed(() => normalizeParetoOptions(chartTask.value?.paretoFront, solutionOptions.value))
const activeSolutionOption = computed(() => {
  const option = solutionOptions.value.find((item) => item.key === selectedOptionKey.value)
  if (option) return option
  const pareto = paretoOptions.value.find((item) => item.key === selectedOptionKey.value)
  if (pareto) return pareto
  return solutionOptions.value[0] || paretoOptions.value[0] || null
})
const selectedPlanCsv = computed(() => {
  const key = activeSolutionOption.value?.key
  return ['balanced', 'economic_min', 'environment_min'].includes(key) ? `dispatch_curves_${key}.csv` : ''
})
const currentSummary = computed(() => {
  const option = activeSolutionOption.value
  if (!option) return chartTask.value?.summary || null
  return {
    ...(chartTask.value?.summary || {}),
    ...option,
    economicCost: n(option.economicCost ?? option.economic_cost),
    environmentCost: n(option.environmentCost ?? option.environment_cost),
    renewableUtilizationRate: n(option.renewableUtilizationRate ?? option.renewable_utilization_rate),
    finalSoc: n(option.finalSoc ?? option.final_soc)
  }
})
const currentCurves = computed(() => {
  if (activeSolutionOption.value) return Array.isArray(activeSolutionOption.value.curves) ? activeSolutionOption.value.curves : []
  return Array.isArray(chartTask.value?.curves) ? chartTask.value.curves : []
})
const curveRows = computed(() => currentCurves.value.map((item, index) => ({
  hour: n(item.time_h ?? item.hour ?? index),
  load: n(item.load_kw ?? item.loadKw),
  pv: n(item.pv_kw ?? item.pvKw),
  wind: n(item.wind_kw ?? item.wt_kw ?? item.wtKw),
  turbine: n(item.diesel_kw ?? item.microTurbineKw),
  battery: n(item.battery_kw ?? item.batteryKw),
  batteryCharge: n(item.battery_charge_kw ?? Math.max(-n(item.battery_kw ?? item.batteryKw), 0)),
  batteryDischarge: n(item.battery_discharge_kw ?? Math.max(n(item.battery_kw ?? item.batteryKw), 0)),
  gridBuy: n(item.grid_buy_kw ?? item.gridBuyKw),
  gridSell: n(item.grid_sell_kw ?? item.gridSellKw),
  grid: n(item.grid_buy_kw ?? item.gridBuyKw) - n(item.grid_sell_kw ?? item.gridSellKw),
  soc: n(item.soc)
})))
const profileSeries = [
  { key: 'load', label: '负荷', color: '#263241' },
  { key: 'pv', label: '光伏', color: '#f2a65a' },
  { key: 'wind', label: '风机', color: '#76b7b2' }
]
const priceSeries = [
  { key: 'buyPrice', label: '购电价', color: '#4e79a7' },
  { key: 'sellPrice', label: '售电价', color: '#f2a65a' }
]
const profilePreviewChart = computed(() => buildLineChart(activeProfileRows.value, profileSeries, '功率 / kW', { yDigits: 0 }))
const pricePreviewChart = computed(() => buildLineChart(activeProfileRows.value, priceSeries, '电价', { maxValue: 1, yDigits: 2 }))
const balanceChart = computed(() => buildBalanceChart(curveRows.value))
const storageChart = computed(() => buildStorageChart(curveRows.value))
const paretoChart = computed(() => buildParetoChart(paretoOptions.value, activeSolutionOption.value))
const cpuHistoryChart = computed(() => buildMetricHistoryChart(serverMetricHistory.value, 'cpu', '#3867a6'))
const memoryHistoryChart = computed(() => buildMetricHistoryChart(serverMetricHistory.value, 'memory', '#167f71'))

const activeRunningTask = computed(() => {
  if (selectedTask.value?.status === 'RUNNING') return selectedTask.value
  if (pollingTaskId.value) {
    const current = tasks.value.find((task) => task.id === pollingTaskId.value && task.status === 'RUNNING')
    if (current) return current
  }
  const mine = serverStatus.value?.runningTaskList?.find((task) => task.mine)
  return mine || tasks.value.find((task) => task.status === 'RUNNING') || null
})
const activeQueuedTask = computed(() => {
  if (selectedTask.value?.status === 'QUEUED') return selectedTask.value
  const mine = serverStatus.value?.queuedTaskList?.find((task) => task.mine)
  return mine || tasks.value.find((task) => task.status === 'QUEUED') || null
})
const activeFloatingTask = computed(() => activeRunningTask.value || activeQueuedTask.value)
const activeFloatingTaskPriority = computed(() => {
  if (activeFloatingTask.value?.status !== 'QUEUED') return null
  return serverStatus.value?.queuedTaskList?.find((task) => task.id === activeFloatingTask.value.id)?.priority || activeFloatingTask.value.priority || null
})
const floatingProgressValue = computed(() => activeFloatingTask.value?.status === 'QUEUED' ? 0 : n(activeFloatingTask.value?.progress))
const runningSlotText = computed(() => {
  if (!serverStatus.value) return '0 / 4'
  return `${serverStatus.value.runningTasks} / ${serverStatus.value.maxRunningTasks}`
})
const kpis = computed(() => {
  const economic = economicMetric(currentSummary.value?.economicCost ?? latestCompletedTask.value?.economicCost)
  return [
    { label: economic.label, value: economic.value, unit: '元', trend: currentSummary.value ? '结果方案' : '最近完成', tone: economic.tone },
    { label: '环境成本', value: formatNumber(currentSummary.value?.environmentCost ?? latestCompletedTask.value?.environmentCost, 0), unit: '', trend: '碳排目标', tone: 'neutral' },
    { label: '可再生消纳率', value: formatPercent(currentSummary.value?.renewableUtilizationRate ?? latestCompletedTask.value?.renewableUtilizationRate), unit: '', trend: '风光利用', tone: 'good' },
    { label: '待处理任务', value: String(serverStatus.value?.queuedTaskList?.length ?? tasks.value.filter((task) => task.status === 'QUEUED').length), unit: '项', trend: '等待计算', tone: 'warn' }
  ]
})
const resourceCards = computed(() => {
  const rows = activeProfileRows.value
  const pvTotal = rows.reduce((sum, row) => sum + n(row.pv), 0)
  const windTotal = rows.reduce((sum, row) => sum + n(row.wind), 0)
  const loadPeak = Math.max(...rows.map((row) => n(row.load)), 0)
  const pvPeak = Math.max(...rows.map((row) => n(row.pv)), 0)
  const windPeak = Math.max(...rows.map((row) => n(row.wind)), 0)
  const sharedPeakScale = niceCeil(Math.max(pvPeak, windPeak, loadPeak, 1))
  const peakFill = (value) => Math.max(4, Math.min(100, n(value) / sharedPeakScale * 100))
  return [
    { label: '光伏预测', icon: Sun, value: `${formatNumber(pvTotal, 0)} kWh`, detail: `峰值 ${formatNumber(pvPeak, 0)} kW`, fill: peakFill(pvPeak), tone: 'solar' },
    { label: '风机预测', icon: Wind, value: `${formatNumber(windTotal, 0)} kWh`, detail: `峰值 ${formatNumber(windPeak, 0)} kW`, fill: peakFill(windPeak), tone: 'wind' },
    { label: '负荷峰值', icon: Activity, value: `${formatNumber(loadPeak, 0)} kW`, detail: hasImportedProfile.value ? '来自导入数据' : '模板预测数据', fill: peakFill(loadPeak), tone: 'load' }
  ]
})
const alerts = computed(() => {
  const list = []
  if (uploadValidationErrors.value.length) list.push({ level: '高', title: '导入数据校验失败', detail: uploadValidationErrors.value[0] })
  if (activeFloatingTask.value) list.push({ level: activeFloatingTask.value.status === 'QUEUED' ? '中' : '高', title: `${activeFloatingTask.value.name} ${statusText(activeFloatingTask.value.status)}`, detail: `进度 ${floatingProgressValue.value}%，预计剩余 ${formatDuration(activeFloatingTask.value.estimatedRemainingSeconds)}` })
  if (!list.length) list.push({ level: '低', title: '当前无阻塞告警', detail: '数据预览、参数配置与调度运行状态正常。' })
  return list
})
const previousParamOptions = computed(() => [
  { key: 'system-default', label: '使用系统默认参数', detail: '来自管理员维护的默认参数' },
  ...(recentConfig.value ? [{ key: 'recent-local', label: '套用上一次任务参数', detail: `${recentConfig.value.taskName || '上一次任务'} · ${recentConfig.value.fileName || '模板文件'}` }] : []),
  ...tasks.value.slice(0, 5).map((task) => ({ key: `task-${task.id}`, label: `套用任务 ${task.id}`, detail: `${task.name} · ${statusText(task.status)}` }))
])
const currentPreviewName = computed(() => {
  const paramName = previousParamOptions.value.find((item) => item.key === previousParamSet.value)?.label || '当前参数'
  return `${activeScenario.value.name} · ${paramName}`
})

function authHeaders() {
  const id = Number(user.value?.id)
  return Number.isFinite(id) && id > 0 ? { 'X-User-Id': String(id) } : {}
}

async function request(path, options = {}) {
  const response = await fetch(`${apiBase}${path}`, options)
  const contentType = response.headers.get('content-type') || ''
  const body = contentType.includes('application/json') ? await response.json() : await response.text()
  if (!response.ok) throw new Error(body?.message || body || '请求失败')
  return body
}

async function submitAuth() {
  loginTransitionReason.value = ''
  if (authForm.captcha.trim() !== captchaText.value) {
    showMessage('验证码错误')
    refreshCaptcha()
    return
  }
  loading.value = true
  try {
    const path = authMode.value === 'login' ? '/api/auth/login' : '/api/auth/register'
    const payload = authMode.value === 'login'
      ? { username: authForm.username, password: authForm.password, role: authRole.value }
      : { username: authForm.username, password: authForm.password }
    const data = await request(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (authMode.value === 'login') {
      loginTransitionReason.value = 'login'
      loginTransition.value = true
      await delay(900)
    }
    user.value = data
    localStorage.setItem(userStorageKey, JSON.stringify(data))
    showMessage(authMode.value === 'login' ? '登录成功' : '注册成功')
    await bootstrapAuthed()
  } catch (error) {
    showMessage(error.message)
    refreshCaptcha()
  } finally {
    loading.value = false
    loginTransition.value = false
    loginTransitionReason.value = ''
  }
}

function refreshCaptcha() {
  captchaText.value = String(Math.floor(1000 + Math.random() * 9000))
  authForm.captcha = ''
}

function delay(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function logout() {
  loginTransition.value = false
  loginTransitionReason.value = ''
  stopPolling()
  stopStatusPolling()
  if (desktopScreenshotUrl.value) URL.revokeObjectURL(desktopScreenshotUrl.value)
  user.value = null
  selectedTask.value = null
  latestCompletedTaskDetail.value = null
  desktopScreenshotUrl.value = ''
  desktopScreenshotError.value = ''
  desktopScreenshotExpanded.value = false
  desktopScreenshotPreviewOpen.value = false
  serverMetricHistory.value = []
  tasks.value = []
  serverStatus.value = null
  serverOnline.value = false
  serverStatusFetchedAt.value = ''
  adminUsers.value = []
  localStorage.removeItem(userStorageKey)
}

async function bootstrapAuthed() {
  const jobs = [loadSettings(), loadTasks(), loadServerStatus({ rethrow: false })]
  if (isAdmin.value) jobs.push(loadAdminUsers())
  jobs.push(loadAlgorithmSettings({ silent: true }))
  const results = await Promise.allSettled(jobs)
  startStatusPolling()
  const rejected = results.find((result) => result.status === 'rejected')
  if (rejected) throw rejected.reason
}

async function loadSettings() {
  const data = await request('/api/settings')
  Object.assign(defaultSettings, settingPayload(data))
  if (previousParamSet.value === 'system-default') {
    Object.assign(taskSettings, settingPayload(data))
  }
}

async function loadAlgorithmSettings({ silent = false } = {}) {
  try {
    const data = await request('/api/settings/algorithm', { headers: authHeaders() })
    Object.assign(algorithmSettings, algorithmSettingPayload(data))
    if (!showTaskAlgorithmOptions.value) {
      Object.assign(taskAlgorithmSettings, algorithmSettingPayload(data))
    }
  } catch (error) {
    if (!silent) showMessage(error.message || 'MOIABC 运行配置加载失败')
  }
}

async function loadTasks({ syncPolling = true, refreshDetail = false } = {}) {
  if (!user.value) return
  tasks.value = await request('/api/dispatch/tasks', { headers: authHeaders() })
  const validIds = new Set(tasks.value.map((task) => task.id))
  selectedTaskIds.value = selectedTaskIds.value.filter((id) => validIds.has(id))
  if (selectedTask.value?.id && !validIds.has(selectedTask.value.id)) selectedTask.value = null
  if (selectedTask.value?.id) {
    await refreshTaskDetail(selectedTask.value.id)
  }
  await syncLatestCompletedTaskDetail(refreshDetail)
  if (syncPolling) syncRunningPolling()
}

async function syncLatestCompletedTaskDetail(force = false) {
  const latest = latestCompletedTask.value
  if (!latest) {
    latestCompletedTaskDetail.value = null
    return
  }
  if (!force && latestCompletedTaskDetail.value?.id === latest.id) return
  try {
    latestCompletedTaskDetail.value = await request(`/api/dispatch/tasks/${latest.id}`, { headers: authHeaders() })
  } catch {
    latestCompletedTaskDetail.value = null
  }
}

async function refreshTaskDetail(id = selectedTask.value?.id) {
  if (!id) return
  const detail = await request(`/api/dispatch/tasks/${id}`, { headers: authHeaders() })
  selectedTask.value = detail
  mergeTaskIntoList(detail)
  if (detail.status === 'RUNNING') startPolling(detail.id)
}

async function loadServerStatus({ rethrow = true } = {}) {
  if (!user.value) return
  try {
    serverStatus.value = await request('/api/system/status', { headers: authHeaders() })
    serverOnline.value = true
    serverStatusFetchedAt.value = new Date().toISOString()
    recordServerMetrics(serverStatus.value)
    syncServerRunningPolling()
  } catch (error) {
    serverStatus.value = null
    serverOnline.value = false
    serverStatusFetchedAt.value = ''
    if (rethrow) throw error
  }
}

async function refreshCurrentModule() {
  if (!user.value) return
  if (refreshingCurrent.value) return
  refreshingCurrent.value = true
  try {
    if (activeModule.value === 'server') {
      await loadServerStatus()
      if (desktopScreenshotExpanded.value) await loadDesktopScreenshot({ silent: true })
    } else if (activeModule.value === 'planner') {
      await Promise.all([loadSettings(), loadTasks({ refreshDetail: true })])
    } else if (activeModule.value === 'admin') {
      await Promise.all([loadSettings(), loadAlgorithmSettings(), loadAdminUsers()])
    } else {
      await loadTasks({ refreshDetail: true })
    }
  } catch (error) {
    showMessage(error.message)
  } finally {
    refreshingCurrent.value = false
  }
}

function recordServerMetrics(status) {
  if (!status) return
  const now = Date.now()
  serverMetricHistory.value = [
    ...serverMetricHistory.value,
    {
      ts: now,
      cpu: n(status.cpuLoadPercent),
      memory: n(status.memoryUsedPercent)
    }
  ].filter((item) => now - item.ts <= 60000)
}

async function loadDesktopScreenshot({ silent = false } = {}) {
  if (!user.value) return
  desktopScreenshotLoading.value = true
  desktopScreenshotError.value = ''
  try {
    const response = await fetch(`${apiBase}/api/system/screenshot?t=${Date.now()}`, {
      headers: authHeaders()
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new Error(body.message || '桌面截图获取失败')
    }
    const blob = await response.blob()
    const nextUrl = URL.createObjectURL(blob)
    if (desktopScreenshotUrl.value) URL.revokeObjectURL(desktopScreenshotUrl.value)
    desktopScreenshotUrl.value = nextUrl
  } catch (error) {
    desktopScreenshotError.value = error.message || '桌面截图获取失败'
    if (!silent) showMessage(desktopScreenshotError.value)
  } finally {
    desktopScreenshotLoading.value = false
  }
}

function toggleDesktopScreenshot() {
  desktopScreenshotExpanded.value = !desktopScreenshotExpanded.value
  if (desktopScreenshotExpanded.value) loadDesktopScreenshot()
}

function openDesktopScreenshotPreview() {
  if (desktopScreenshotUrl.value) desktopScreenshotPreviewOpen.value = true
}

async function loadAdminUsers() {
  if (!isAdmin.value) return
  adminUsers.value = await request('/api/admin/users', { headers: authHeaders() })
}

async function updateUserRole(targetUser, role) {
  loading.value = true
  try {
    const updated = await request(`/api/admin/users/${targetUser.id}/role`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ role })
    })
    adminUsers.value = adminUsers.value.map((item) => item.id === updated.id ? updated : item)
    showMessage('用户权限已更新')
  } catch (error) {
    showMessage(error.message)
  } finally {
    loading.value = false
  }
}

function openFilePicker() {
  fileInputRef.value?.click()
}

async function onFileChange(event) {
  const file = event.target.files?.[0] || null
  uploadForm.file = file
  uploadValidationErrors.value = []
  importedProfileRows.value = []
  selectedScenarioKey.value = null
  if (!file) return
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['csv', 'xlsx', 'xlsm', 'xls'].includes(extension)) {
    uploadValidationErrors.value = ['仅支持 CSV、XLSX、XLSM、XLS 格式']
    uploadForm.file = null
    return
  }
  await previewProfile()
}

async function previewProfile() {
  if (!uploadForm.file || !user.value) return
  previewLoading.value = true
  uploadValidationErrors.value = []
  try {
    const form = new FormData()
    form.append('file', uploadForm.file)
    const rows = await request('/api/dispatch/preview', {
      method: 'POST',
      headers: authHeaders(),
      body: form
    })
    importedProfileRows.value = normalizeProfileRows(rows)
    uploadValidationErrors.value = validateProfileRows(importedProfileRows.value)
    if (uploadValidationErrors.value.length) showMessage(uploadValidationErrors.value[0])
  } catch (error) {
    uploadForm.file = null
    importedProfileRows.value = []
    uploadValidationErrors.value = [error.message || '数据文件解析失败']
    showMessage(uploadValidationErrors.value[0])
  } finally {
    previewLoading.value = false
  }
}

function clearSelectedFile() {
  uploadForm.file = null
  importedProfileRows.value = []
  uploadValidationErrors.value = []
  selectedScenarioKey.value = 'template'
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function applyScenario(scenarioKey) {
  const scenario = dispatchScenarios.find((item) => item.key === scenarioKey)
  if (!scenario) return
  selectedScenarioKey.value = scenario.key
  uploadValidationErrors.value = []
  importedProfileRows.value = scenario.key === 'template' ? [] : normalizeProfileRows(scenario.rows)
  uploadForm.file = scenario.key === 'template' ? null : scenarioFile(scenario)
  if (fileInputRef.value) fileInputRef.value.value = ''
  const errors = validateProfileRows(activeProfileRows.value)
  uploadValidationErrors.value = errors
  if (errors.length) {
    showMessage(errors[0])
  } else {
    showMessage(`已切换到${scenario.name}`)
  }
}

function scenarioFile(scenario) {
  const header = 'hour,buy_price,sell_price,load_kw,pv_kw,wt_kw'
  const rows = scenario.rows.map((row) => [
    row.hour,
    row.buyPrice,
    row.sellPrice,
    row.load,
    row.pv,
    row.wind
  ].join(','))
  return new File([[header, ...rows].join('\n')], scenario.filename, { type: 'text/csv' })
}

async function confirmUploadTask() {
  const errors = validateProfileRows(activeProfileRows.value)
  if (errors.length) {
    uploadValidationErrors.value = errors
    showMessage(errors[0])
    return
  }
  showConfirm.value = true
}

async function uploadTask() {
  showConfirm.value = false
  loading.value = true
  submittingTask.value = true
  try {
    const form = new FormData()
    const taskName = uploadForm.name?.trim() || defaultTaskName()
    uploadForm.name = taskName
    form.append('name', taskName)
    form.append('file', uploadForm.file || await templateFile())
    form.append('settings', JSON.stringify(settingPayload(taskSettings)))
    if (showTaskAlgorithmOptions.value) {
      form.append('algorithmSettings', JSON.stringify(algorithmSettingPayload(taskAlgorithmSettings)))
    }
    const detail = await request('/api/dispatch/tasks', {
      method: 'POST',
      headers: authHeaders(),
      body: form
    })
    selectedTask.value = detail
    activeModule.value = 'overview'
    saveRecentConfig(taskName)
    mergeTaskIntoList(detail)
    startPolling(detail.id)
    await Promise.all([loadTasks({ syncPolling: false }), loadServerStatus()])
    showMessage('调度任务已提交')
  } catch (error) {
    showMessage(error.message || '提交失败')
  } finally {
    loading.value = false
    submittingTask.value = false
  }
}

async function openTask(id) {
  selectedTask.value = await request(`/api/dispatch/tasks/${id}`, { headers: authHeaders() })
  activeModule.value = 'overview'
  if (selectedTask.value.status === 'RUNNING') startPolling(id)
}

async function cancelTask(id = activeFloatingTask.value?.id || selectedTask.value?.id) {
  if (!id) return
  if (!window.confirm('确定取消当前调度任务吗？')) return
  loading.value = true
  try {
    const detail = await request(`/api/dispatch/tasks/${id}/cancel`, { method: 'POST', headers: authHeaders() })
    mergeTaskIntoList(detail)
    selectedTask.value = detail
    if (pollingTaskId.value === id) stopPolling()
    await Promise.all([loadTasks({ syncPolling: false }), loadServerStatus()])
    showMessage('调度任务已取消')
  } catch (error) {
    showMessage(error.message)
  } finally {
    loading.value = false
  }
}

async function deleteSelectedTasks() {
  if (!selectedTaskIds.value.length) return
  if (!window.confirm(`确定删除选中的 ${selectedTaskIds.value.length} 条历史记录吗？`)) return
  loading.value = true
  try {
    const params = new URLSearchParams()
    selectedTaskIds.value.forEach((id) => params.append('ids', id))
    await request(`/api/dispatch/tasks?${params.toString()}`, { method: 'DELETE', headers: authHeaders() })
    selectedTaskIds.value = []
    await loadTasks()
    showMessage('已删除选中的历史记录')
  } catch (error) {
    showMessage(error.message)
  } finally {
    loading.value = false
  }
}

async function downloadResult(filename) {
  if (!chartTask.value?.id) return
  const response = await fetch(`${apiBase}/api/dispatch/tasks/${chartTask.value.id}/download/${filename}`, {
    headers: authHeaders()
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    showMessage(body.message || '下载失败')
    return
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function downloadTemplate() {
  window.open(`${apiBase}/api/dispatch/template`, '_blank')
}

async function templateFile() {
  const response = await fetch(`${apiBase}/api/dispatch/template`)
  if (!response.ok) throw new Error('默认模板加载失败')
  const blob = await response.blob()
  return new File([blob], 'microgrid_dispatch_template.csv', { type: 'text/csv' })
}

async function applyParameterSource() {
  if (previousParamSet.value === 'system-default') {
    Object.assign(taskSettings, settingPayload(defaultSettings))
    showMessage('已套用系统默认参数')
    return
  }
  if (previousParamSet.value === 'recent-local' && recentConfig.value?.settings) {
    Object.assign(taskSettings, settingPayload(recentConfig.value.settings))
    uploadForm.name = recentConfig.value.taskName || uploadForm.name
    showMessage('已套用上一次任务参数')
    return
  }
  if (previousParamSet.value.startsWith('task-')) {
    try {
      const id = previousParamSet.value.replace('task-', '')
      const detail = await request(`/api/dispatch/tasks/${id}`, { headers: authHeaders() })
      const settings = detail?.input?.settings
      if (!settings) throw new Error('该任务没有可复用的参数快照')
      Object.assign(taskSettings, settingPayload(settings))
      showMessage('已套用历史任务参数')
    } catch (error) {
      showMessage(error.message)
    }
  }
}

async function saveDefaultSettings() {
  if (!isAdmin.value) return
  loading.value = true
  try {
    const data = await request('/api/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify(settingPayload(defaultSettings))
    })
    Object.assign(defaultSettings, settingPayload(data))
    if (previousParamSet.value === 'system-default') Object.assign(taskSettings, settingPayload(data))
    showMessage('系统默认参数已更新')
  } catch (error) {
    showMessage(error.message)
  } finally {
    loading.value = false
  }
}

async function saveAlgorithmSettings() {
  if (!isAdmin.value) return
  loading.value = true
  try {
    const data = await request('/api/settings/algorithm', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify(algorithmSettingPayload(algorithmSettings))
    })
    Object.assign(algorithmSettings, algorithmSettingPayload(data))
    if (!showTaskAlgorithmOptions.value) {
      Object.assign(taskAlgorithmSettings, algorithmSettingPayload(data))
    }
    showMessage('MOIABC 运行配置已更新')
  } catch (error) {
    showMessage(error.message)
  } finally {
    loading.value = false
  }
}

function startPolling(taskId) {
  if (!taskId) return
  if (pollTimer && pollingTaskId.value === taskId) return
  stopPolling()
  pollingTaskId.value = taskId
  const tick = async () => {
    if (pollingRequestActive || pollingTaskId.value !== taskId) return
    pollingRequestActive = true
    try {
      const detail = await request(`/api/dispatch/tasks/${taskId}`, { headers: authHeaders() })
      if (pollingTaskId.value !== taskId) return
      mergeTaskIntoList(detail)
      if (selectedTask.value?.id === taskId) selectedTask.value = detail
      if (['COMPLETED', 'FAILED', 'PAUSED', 'CANCELED'].includes(detail.status)) {
        stopPolling()
        await Promise.all([loadTasks({ syncPolling: false }), loadServerStatus()])
        showMessage(detail.status === 'COMPLETED' ? '算法计算完成' : detail.message || '任务已结束')
      }
    } catch (error) {
      stopPolling()
      showMessage(error.message)
    } finally {
      pollingRequestActive = false
    }
  }
  tick()
  pollTimer = window.setInterval(tick, 1000)
}

function stopPolling() {
  if (pollTimer) window.clearInterval(pollTimer)
  pollTimer = null
  pollingTaskId.value = null
}

function startStatusPolling() {
  stopStatusPolling()
  if (!user.value) return
  statusTimer = window.setInterval(() => {
    loadServerStatus({ rethrow: false })
  }, 3000)
}

function stopStatusPolling() {
  if (statusTimer) window.clearInterval(statusTimer)
  statusTimer = null
}

function syncRunningPolling() {
  const running = tasks.value.find((task) => task.status === 'RUNNING')
  if (running) startPolling(running.id)
  else if (pollingTaskId.value) stopPolling()
}

function syncServerRunningPolling() {
  const mine = serverStatus.value?.runningTaskList?.find((task) => task.mine)
  if (mine) {
    mergeTaskIntoList(mine)
    if (pollingTaskId.value !== mine.id) startPolling(mine.id)
  }
}

function mergeTaskIntoList(detail) {
  const index = tasks.value.findIndex((task) => task.id === detail.id)
  const next = index >= 0 ? { ...tasks.value[index], ...detail } : detail
  tasks.value = index >= 0 ? tasks.value.map((task) => task.id === detail.id ? next : task) : [next, ...tasks.value]
}

function toggleTaskSelection(id, checked) {
  const next = new Set(selectedTaskIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  selectedTaskIds.value = [...next]
}

function buildLineChart(rows, series, yTitle, options = {}) {
  const width = 1000
  const height = 450
  const left = 64
  const right = 34
  const top = 24
  const bottom = 54
  const values = rows.flatMap((row) => series.map((item) => n(row[item.key])))
  const maxValue = options.maxValue ?? niceCeil(Math.max(...values, 1))
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  const xOf = (index) => left + (n(rows[index]?.hour ?? index) / 24) * plotWidth
  const yOf = (value) => top + (1 - n(value) / maxValue) * plotHeight
  return {
    width,
    height,
    plotLeft: left,
    plotRight: width - right,
    plotTop: top,
    plotBottom: height - bottom,
    xTitleY: height - 18,
    yTitleY: top + plotHeight / 2,
    yDigits: options.yDigits ?? 0,
    yTitle,
    yLabels: [maxValue, maxValue * 0.5, 0],
    yLines: [top, top + plotHeight * 0.5, top + plotHeight],
    xTicks: [0, 4, 8, 12, 16, 20, 24].map((hour) => ({ hour, x: left + (hour / 24) * plotWidth })),
    lines: series.map((item) => ({
      ...item,
      points: rows.map((row, index) => `${xOf(index)},${yOf(row[item.key])}`).join(' '),
      markers: rows.map((row, index) => ({ x: xOf(index), y: yOf(row[item.key]), value: n(row[item.key]), hour: row.hour }))
    }))
  }
}

function buildBalanceChart(rows) {
  const width = 1000
  const height = 480
  const left = 64
  const right = 34
  const top = 30
  const bottom = 56
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  const positiveTotals = rows.map((row) => n(row.wind) + n(row.pv) + n(row.turbine) + n(row.gridBuy) + n(row.batteryDischarge))
  const negativeTotals = rows.map((row) => n(row.batteryCharge) + n(row.gridSell))
  const maxPositive = niceCeil(Math.max(...positiveTotals, ...rows.map((row) => n(row.load)), 1))
  const maxNegative = niceCeil(Math.max(...negativeTotals, 1))
  const zeroY = top + plotHeight * (maxPositive / (maxPositive + maxNegative))
  const scale = plotHeight / (maxPositive + maxNegative)
  const step = plotWidth / Math.max(rows.length, 1)
  const barWidth = Math.max(6, step * 0.72)
  const xOf = (index) => left + (index + 0.5) * step
  const hitZones = rows.map((row, index) => ({
    key: `balance-hit-${index}`,
    x: left + index * step,
    y: top,
    width: step,
    height: plotHeight,
    hour: row.hour
  }))
  const bars = []
  rows.forEach((row, index) => {
    const x = xOf(index) - barWidth / 2
    let yTop = zeroY
    let yBottom = zeroY
    const positiveCells = [
      ['wind', '风电', n(row.wind), '#4e79a7'],
      ['pv', '光伏', n(row.pv), '#f2c14e'],
      ['turbine', '微型燃气轮机', n(row.turbine), '#9c7a52'],
      ['gridBuy', '主网购电', n(row.gridBuy), '#59a14f'],
      ['batteryDischarge', '储能放电', n(row.batteryDischarge), '#e15759']
    ]
    const negativeCells = [
      ['batteryCharge', '储能充电', n(row.batteryCharge), '#af7aa1'],
      ['gridSell', '主网售电', n(row.gridSell), '#76b7b2']
    ]
    positiveCells.forEach(([key, label, value, color]) => {
      if (value <= 0) return
      const h = value * scale
      yTop -= h
      bars.push({ key: `${index}-${key}`, rowIndex: index, label, value, x, y: yTop, width: barWidth, height: h, color })
    })
    negativeCells.forEach(([key, label, value, color]) => {
      if (value <= 0) return
      const h = value * scale
      bars.push({ key: `${index}-${key}`, rowIndex: index, label, value, x, y: yBottom, width: barWidth, height: h, color })
      yBottom += h
    })
  })
  const loadMarkers = rows.map((row, index) => ({ x: xOf(index), y: zeroY - n(row.load) * scale, value: n(row.load), hour: row.hour }))
  const yTicks = [
    { value: maxPositive, y: top },
    { value: maxPositive * 0.5, y: top + (zeroY - top) * 0.5 },
    { value: 0, y: zeroY },
    { value: -maxNegative * 0.5, y: zeroY + (height - bottom - zeroY) * 0.5 },
    { value: -maxNegative, y: height - bottom }
  ]
  return {
    width,
    height,
    zeroY,
    bars,
    hitZones,
    loadPoints: loadMarkers.map((point) => `${point.x},${point.y}`).join(' '),
    loadMarkers,
    yTicks,
    yLines: yTicks.map((tick) => tick.y),
    xTicks: [0, 4, 8, 12, 16, 20, 24].map((hour) => ({ hour, x: left + (hour / 24) * plotWidth }))
  }
}

function buildStorageChart(rows) {
  const width = 1000
  const height = 430
  const left = 64
  const right = 58
  const top = 28
  const bottom = 52
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  const maxPower = niceCeil(Math.max(...rows.flatMap((row) => [
    Math.abs(n(row.battery)),
    n(row.batteryCharge),
    n(row.batteryDischarge)
  ]), 1))
  const zeroY = top + plotHeight / 2
  const yPower = (value) => zeroY - n(value) / maxPower * (plotHeight / 2)
  const ySoc = (value) => top + (1 - normalizeSoc(value)) * plotHeight
  const step = plotWidth / Math.max(rows.length, 1)
  const barWidth = Math.max(6, step * 0.72)
  const xOf = (index) => left + (index + 0.5) * step
  const hitZones = rows.map((row, index) => ({
    key: `storage-hit-${index}`,
    x: left + index * step,
    y: top,
    width: step,
    height: plotHeight,
    hour: row.hour
  }))
  const dischargeBars = rows.map((row, index) => {
    const value = n(row.batteryDischarge)
    const y = yPower(value)
    return { key: `discharge-${index}`, x: xOf(index) - barWidth / 2, y, width: barWidth, height: zeroY - y, value }
  }).filter((bar) => bar.value > 0)
  const chargeBars = rows.map((row, index) => {
    const value = n(row.batteryCharge)
    return { key: `charge-${index}`, x: xOf(index) - barWidth / 2, y: zeroY, width: barWidth, height: yPower(-value) - zeroY, value }
  }).filter((bar) => bar.value > 0)
  const netMarkers = rows.map((row, index) => ({ x: xOf(index), y: yPower(row.battery), value: n(row.battery), hour: row.hour }))
  const socMarkers = rows.map((row, index) => ({ x: xOf(index), y: ySoc(row.soc), value: normalizeSoc(row.soc), hour: row.hour }))
  const yTicks = [
    { value: maxPower, y: top },
    { value: maxPower * 0.5, y: top + plotHeight * 0.25 },
    { value: 0, y: zeroY },
    { value: -maxPower * 0.5, y: top + plotHeight * 0.75 },
    { value: -maxPower, y: top + plotHeight }
  ]
  const socTicks = [
    { value: 1, y: top, label: '100%' },
    { value: 0.5, y: zeroY, label: '50%' },
    { value: 0, y: top + plotHeight, label: '0%' }
  ]
  return {
    width,
    height,
    zeroY,
    maxPower,
    yTicks,
    yLines: yTicks.map((tick) => tick.y),
    socTicks,
    dischargeBars,
    chargeBars,
    hitZones,
    netPoints: netMarkers.map((point) => `${point.x},${point.y}`).join(' '),
    netMarkers,
    socPoints: socMarkers.map((point) => `${point.x},${point.y}`).join(' '),
    socMarkers,
    xTicks: [0, 4, 8, 12, 16, 20, 24].map((hour) => ({ hour, x: left + (hour / 24) * plotWidth }))
  }
}

function normalizeParetoOptions(rawPoints, options) {
  const optionByIndex = new Map()
  const optionByKey = new Map()
  options.forEach((option) => {
    const index = Number(option.paretoIndex ?? option.index)
    if (Number.isFinite(index) && !optionByIndex.has(index)) optionByIndex.set(index, option)
    if (option.key) optionByKey.set(option.key, option)
  })
  const source = Array.isArray(rawPoints) && rawPoints.length
    ? rawPoints
    : options.map((option, index) => ({ ...option, index: option.paretoIndex ?? index + 1 }))
  return source
    .map((point, index) => {
      const paretoIndex = Number(point.paretoIndex ?? point.index ?? index + 1)
      const linkedOption = optionByIndex.get(paretoIndex) || optionByKey.get(point.key)
      const key = linkedOption?.key || point.key || `pareto_${paretoIndex}`
      return {
        ...point,
        ...(linkedOption || {}),
        key,
        paretoIndex,
        label: linkedOption?.label || point.label || `Pareto 方案 ${paretoIndex}`,
        description: linkedOption?.description || point.description || 'Pareto 前沿上的可选调度方案。',
        economicCost: n(point.economicCost ?? point.economic_cost ?? linkedOption?.economicCost ?? linkedOption?.economic_cost),
        environmentCost: n(point.environmentCost ?? point.environment_cost ?? linkedOption?.environmentCost ?? linkedOption?.environment_cost),
        penalizedEconomicObjective: n(point.penalizedEconomicObjective ?? point.penalized_economic_objective ?? linkedOption?.penalizedEconomicObjective ?? linkedOption?.penalized_economic_objective),
        penalizedEnvironmentObjective: n(point.penalizedEnvironmentObjective ?? point.penalized_environment_objective ?? linkedOption?.penalizedEnvironmentObjective ?? linkedOption?.penalized_environment_objective),
        renewableUtilizationRate: n(point.renewableUtilizationRate ?? point.renewable_utilization_rate ?? linkedOption?.renewableUtilizationRate ?? linkedOption?.renewable_utilization_rate),
        finalSoc: n(point.finalSoc ?? point.final_soc ?? linkedOption?.finalSoc ?? linkedOption?.final_soc),
        isCompromise: Boolean(point.isCompromise ?? point.is_compromise ?? linkedOption?.isCompromise),
        isEconomicBest: Boolean(point.isEconomicBest ?? point.is_economic_best ?? linkedOption?.key === 'economic_min'),
        isEnvironmentBest: Boolean(point.isEnvironmentBest ?? point.is_environment_best ?? linkedOption?.key === 'environment_min'),
        curves: Array.isArray(point.curves) ? point.curves : linkedOption?.curves
      }
    })
    .filter((point) => Number.isFinite(point.economicCost) && Number.isFinite(point.environmentCost))
}

function buildParetoChart(points, activeOption) {
  const width = 1000
  const height = 350
  const left = 72
  const right = 34
  const top = 30
  const bottom = 58
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  if (!points.length) {
    return { width, height, markers: [], linePoints: '', xTicks: [], yTicks: [] }
  }
  const xValues = points.map((point) => n(point.economicCost))
  const yValues = points.map((point) => n(point.environmentCost))
  const xRange = paddedRange(Math.min(...xValues), Math.max(...xValues))
  const yRange = paddedRange(Math.min(...yValues), Math.max(...yValues))
  const xOf = (value) => left + (n(value) - xRange.min) / (xRange.max - xRange.min) * plotWidth
  const yOf = (value) => top + (1 - (n(value) - yRange.min) / (yRange.max - yRange.min)) * plotHeight
  const markers = points.map((point) => ({
    ...point,
    x: xOf(point.economicCost),
    y: yOf(point.environmentCost),
    active: activeOption?.key === point.key || Number(activeOption?.paretoIndex) === Number(point.paretoIndex)
  }))
  const linePoints = [...markers]
    .sort((a, b) => a.economicCost - b.economicCost)
    .map((point) => `${point.x},${point.y}`)
    .join(' ')
  const tickValues = (range) => [range.min, (range.min + range.max) / 2, range.max]
  return {
    width,
    height,
    markers,
    linePoints,
    xTicks: tickValues(xRange).map((value) => ({ value, x: xOf(value) })),
    yTicks: tickValues(yRange).map((value) => ({ value, y: yOf(value) })),
    plot: { left, right: width - right, top, bottom: height - bottom }
  }
}

function paddedRange(min, max) {
  if (!Number.isFinite(min) || !Number.isFinite(max)) return { min: 0, max: 1 }
  if (min === max) {
    const padding = Math.max(1, Math.abs(min) * 0.05)
    return { min: min - padding, max: max + padding }
  }
  const padding = (max - min) * 0.12
  return { min: min - padding, max: max + padding }
}

function buildMetricHistoryChart(rows, key, color) {
  const width = 1000
  const height = 260
  const left = 54
  const right = 24
  const top = 24
  const bottom = 42
  const plotWidth = width - left - right
  const plotHeight = height - top - bottom
  const now = Date.now()
  const safeRows = rows.length ? rows : [{ ts: now, [key]: 0 }]
  const xOf = (ts) => left + Math.max(0, Math.min(1, (ts - (now - 60000)) / 60000)) * plotWidth
  const yOf = (value) => top + (1 - Math.max(0, Math.min(100, n(value))) / 100) * plotHeight
  const markers = safeRows.map((row) => ({
    x: xOf(row.ts),
    y: yOf(row[key]),
    value: n(row[key]),
    age: Math.max(0, Math.round((now - row.ts) / 1000))
  }))
  return {
    width,
    height,
    color,
    points: markers.map((point) => `${point.x},${point.y}`).join(' '),
    markers,
    yTicks: [100, 75, 50, 25, 0].map((value) => ({ value, y: yOf(value) })),
    xTicks: [60, 45, 30, 15, 0].map((secondsAgo) => ({
      secondsAgo,
      label: secondsAgo === 0 ? '现在' : `-${secondsAgo}s`,
      x: xOf(now - secondsAgo * 1000)
    }))
  }
}

function selectParetoPoint(point) {
  if (!point?.key) return
  selectedOptionKey.value = point.key
}

function paretoHover(event, point) {
  if (!point) return
  activeChartHover.chart = 'pareto'
  activeChartHover.index = point.paretoIndex
  tooltip.show = true
  tooltip.x = event.clientX + 14
  tooltip.y = event.clientY + 14
  tooltip.title = point.label
  tooltip.lines = [
    `经济成本: ${formatNumber(point.economicCost, 2)}`,
    `环境成本: ${formatNumber(point.environmentCost, 2)}`,
    `可再生能源利用率: ${formatPercent(point.renewableUtilizationRate)}`,
    point.curves?.length ? '点击切换到该方案' : '该历史点缺少曲线明细'
  ]
}

function niceCeil(value) {
  const raw = Math.max(1, n(value))
  if (raw <= 10) return Math.ceil(raw)
  if (raw <= 50) return Math.ceil(raw / 5) * 5
  return Math.ceil(raw / 10) * 10
}

function normalizeSoc(value) {
  const raw = n(value)
  const normalized = raw > 1 ? raw / 100 : raw
  return Math.max(0, Math.min(1, normalized))
}

function chartHover(event, rows, index, chart) {
  const row = rows[index]
  if (!row) return
  activeChartHover.chart = chart
  activeChartHover.index = index
  tooltip.show = true
  tooltip.x = event.clientX + 14
  tooltip.y = event.clientY + 14
  tooltip.title = `${formatNumber(row.hour, 1)} h`
  const lines = [
    `负荷: ${formatNumber(row.load, 2)} kW`,
    `光伏: ${formatNumber(row.pv, 2)} kW`,
    `风机: ${formatNumber(row.wind, 2)} kW`
  ]
  if (Number.isFinite(Number(row.turbine))) lines.push(`燃气轮机: ${formatNumber(row.turbine, 2)} kW`)
  if (Number.isFinite(Number(row.battery))) lines.push(`储能: ${formatNumber(row.battery, 2)} kW`)
  if (Number.isFinite(Number(row.grid))) lines.push(`主网: ${formatNumber(row.grid, 2)} kW`)
  tooltip.lines = lines
}

function priceHover(event, rows, index) {
  const row = rows[index]
  if (!row) return
  activeChartHover.chart = 'price-preview'
  activeChartHover.index = index
  tooltip.show = true
  tooltip.x = event.clientX + 14
  tooltip.y = event.clientY + 14
  tooltip.title = `${formatNumber(row.hour, 1)} h`
  tooltip.lines = [
    `购电价: ${formatNumber(row.buyPrice, 2)}`,
    `售电价: ${formatNumber(row.sellPrice, 2)}`
  ]
}

function balanceHover(event, rows, index) {
  const row = rows[index]
  if (!row) return
  activeChartHover.chart = 'balance'
  activeChartHover.index = index
  tooltip.show = true
  tooltip.x = event.clientX + 14
  tooltip.y = event.clientY + 14
  const supply = n(row.wind) + n(row.pv) + n(row.turbine) + n(row.gridBuy) + n(row.batteryDischarge)
  const absorb = n(row.batteryCharge) + n(row.gridSell)
  tooltip.title = `${formatNumber(row.hour, 1)} h`
  tooltip.lines = [
    `负荷: ${formatNumber(row.load, 2)} kW`,
    `供给合计: ${formatNumber(supply, 2)} kW`,
    `吸收合计: ${formatNumber(absorb, 2)} kW`
  ]
}

function storageHover(event, rows, index) {
  const row = rows[index]
  if (!row) return
  activeChartHover.chart = 'storage'
  activeChartHover.index = index
  tooltip.show = true
  tooltip.x = event.clientX + 14
  tooltip.y = event.clientY + 14
  tooltip.title = `${formatNumber(row.hour, 1)} h`
  tooltip.lines = [
    `储能净功率: ${formatNumber(row.battery, 2)} kW`,
    `储能放电: ${formatNumber(row.batteryDischarge, 2)} kW`,
    `储能充电: ${formatNumber(row.batteryCharge, 2)} kW`,
    `荷电状态: ${formatPercent(normalizeSoc(row.soc))}`
  ]
}

function metricHover(event, chart, index, title) {
  const point = chart.markers[index]
  if (!point) return
  activeChartHover.chart = title
  activeChartHover.index = index
  tooltip.show = true
  tooltip.x = event.clientX + 14
  tooltip.y = event.clientY + 14
  tooltip.title = `${title} · ${point.age === 0 ? '当前' : `${point.age} 秒前`}`
  tooltip.lines = [`占用率: ${formatNumber(point.value, 1)}%`]
}

function chartMove(event) {
  if (!tooltip.show) return
  tooltip.x = event.clientX + 14
  tooltip.y = event.clientY + 14
}

function chartLeave() {
  tooltip.show = false
  activeChartHover.chart = ''
  activeChartHover.index = null
}

function showHelpTooltip(event, title, lines) {
  const nextLines = Array.isArray(lines) ? lines : [lines]
  window.clearTimeout(helpTooltipTimer)
  tooltip.show = true
  tooltip.x = event.clientX + 14
  tooltip.y = event.clientY + 14
  tooltip.title = title
  tooltip.lines = nextLines
  activeChartHover.chart = ""
  activeChartHover.index = null
  helpTooltipTimer = window.setTimeout(() => {
    tooltip.show = false
  }, 3500)
}

function normalizeProfileRows(rows) {
  return (Array.isArray(rows) ? rows : []).map((row, index) => ({
    hour: n(row.hour ?? index),
    buyPrice: n(row.buyPrice ?? row.buy_price),
    sellPrice: n(row.sellPrice ?? row.sell_price),
    load: n(row.load ?? row.loadKw ?? row.load_kw),
    pv: n(row.pv ?? row.pvKw ?? row.pv_kw),
    wind: n(row.wind ?? row.wtKw ?? row.windKw ?? row.wind_kw ?? row.wt_kw)
  }))
}

function validateProfileRows(rows) {
  const errors = []
  if (!Array.isArray(rows) || !rows.length) return ['数据文件没有可解析的数据行']
  if (rows.length !== 24) errors.push(`数据文件需要 24 行小时数据，当前为 ${rows.length} 行`)
  rows.forEach((row, index) => {
    for (const key of ['hour', 'buyPrice', 'sellPrice', 'load', 'pv', 'wind']) {
      if (!Number.isFinite(Number(row[key]))) errors.push(`第 ${index + 1} 行 ${key} 不是有效数字`)
    }
  })
  return errors.slice(0, 4)
}

function statusText(status) {
  const map = { RUNNING: '运行中', QUEUED: '待处理', COMPLETED: '已完成', FAILED: '失败', PAUSED: '已暂停', CANCELED: '已取消' }
  return map[status] || status || '-'
}

function taskClass(status) {
  if (status === 'RUNNING') return 'running'
  if (status === 'QUEUED') return 'queued'
  if (status === 'PAUSED' || status === 'CANCELED') return 'paused'
  if (status === 'FAILED') return 'failed'
  return 'done'
}

function priorityText(priority) {
  const map = { HIGH: '高优先', MEDIUM: '中优先', LOW: '低优先' }
  return map[priority] || '中优先'
}

function priorityClass(priority) {
  if (priority === 'HIGH') return 'high'
  if (priority === 'LOW') return 'low'
  return 'medium'
}

function settingPayload(source) {
  return Object.fromEntries(settingFields.map(([key]) => [key, n(source[key])]))
}

function algorithmSettingPayload(source) {
  return Object.fromEntries(algorithmSettingFields.map(([key]) => {
    const raw = source?.[key]
    const value = raw === undefined || raw === null || raw === '' ? n(algorithmSettings[key]) : n(raw)
    return [
      key,
      algorithmIntegerKeys.has(key)
        ? Math.max(1, Math.round(value))
        : Math.max(0, Math.min(1, Number(value.toFixed(4))))
    ]
  }))
}

function algorithmFieldStep(key) {
  return algorithmIntegerKeys.has(key) ? 1 : 0.01
}

function algorithmFieldMin(key) {
  return algorithmIntegerKeys.has(key) ? 1 : 0
}

function algorithmFieldMax(key) {
  return algorithmIntegerKeys.has(key) ? undefined : 1
}

function readStoredUser() {
  try {
    const raw = localStorage.getItem(userStorageKey)
    const parsed = raw ? JSON.parse(raw) : null
    if (Number(parsed?.id) > 0) return parsed
  } catch {
    localStorage.removeItem(userStorageKey)
  }
  return null
}

function readRecentConfig() {
  try {
    const raw = localStorage.getItem(recentConfigKey)
    return raw ? JSON.parse(raw) : null
  } catch {
    localStorage.removeItem(recentConfigKey)
    return null
  }
}

function saveRecentConfig(taskName) {
  const config = {
    taskName,
    fileName: selectedDataFileName.value,
    settings: settingPayload(taskSettings),
    savedAt: new Date().toISOString()
  }
  recentConfig.value = config
  localStorage.setItem(recentConfigKey, JSON.stringify(config))
}

function showMessage(text) {
  message.value = text
  window.clearTimeout(messageTimer)
  messageTimer = window.setTimeout(() => { message.value = '' }, 2600)
}

function formatDate(value) {
  if (!value) return ''
  if (Array.isArray(value)) return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')} ${String(value[3] || 0).padStart(2, '0')}:${String(value[4] || 0).padStart(2, '0')}`
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatNumber(value, digits = 1) {
  return n(value).toFixed(digits)
}

function economicMetric(value) {
  const amount = n(value)
  const profitable = amount < 0
  return {
    label: profitable ? '运行盈利' : '运行成本',
    value: formatNumber(Math.abs(amount), 0),
    tone: profitable ? 'good' : 'neutral'
  }
}

function economicText(value) {
  const amount = n(value)
  return `${amount < 0 ? '盈利' : '经济'} ${formatNumber(Math.abs(amount), 0)}`
}

function formatPercent(value) {
  const number = n(value)
  if (!number) return '0%'
  return `${formatNumber(number * 100, 1)}%`
}

function formatDuration(seconds) {
  const safe = Math.max(0, Math.round(n(seconds)))
  if (safe < 60) return `${safe} 秒`
  return `${Math.floor(safe / 60)} 分 ${safe % 60} 秒`
}

function defaultTaskName(date = new Date()) {
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日${String(date.getHours()).padStart(2, '0')}时${String(date.getMinutes()).padStart(2, '0')}分微电网优化调度`
}

function n(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

onMounted(async () => {
  refreshCaptcha()
  try {
    await loadSettings()
    if (user.value) {
      await bootstrapAuthed()
    }
  } catch (error) {
    if (user.value) {
      startStatusPolling()
    }
    showMessage(error.message)
  }
})

onUnmounted(() => {
  stopPolling()
  stopStatusPolling()
  window.clearTimeout(messageTimer)
  if (desktopScreenshotUrl.value) URL.revokeObjectURL(desktopScreenshotUrl.value)
})

watch(previousParamSet, applyParameterSource)
watch(authMode, refreshCaptcha)
watch(() => user.value?.role, () => {
  if (!isAdmin.value && activeModule.value === 'admin') activeModule.value = 'overview'
})
watch(solutionOptions, (options) => {
  if (options.length && !options.some((option) => option.key === selectedOptionKey.value)) {
    selectedOptionKey.value = options[0].key
  }
})
</script>

<template>
  <main class="app-shell" :class="{ 'is-auth': !user }">
    <aside v-if="user" class="sidebar" :class="{ open: sidebarOpen }">
      <div class="brand">
        <div class="brand-mark"><Zap :size="21" /></div>
        <div>
          <strong>MG Dispatch</strong>
          <span>微电网调度中心</span>
        </div>
      </div>

      <nav class="module-nav">
        <button
          v-for="item in modules"
          :key="item.key"
          type="button"
          :class="{ active: activeModule === item.key }"
          @click="activeModule = item.key; sidebarOpen = false"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <section v-if="activeFloatingTask" class="sidebar-task" :class="taskClass(activeFloatingTask.status)">
        <div class="sidebar-task-head">
          <span>{{ statusText(activeFloatingTask.status) }}</span>
          <b v-if="activeFloatingTask.status === 'QUEUED'">{{ priorityText(activeFloatingTaskPriority) }}</b>
          <b v-else>{{ floatingProgressValue }}%</b>
        </div>
        <strong>{{ activeFloatingTask.name }}</strong>
        <div class="progress-track"><i :style="{ width: `${floatingProgressValue}%` }"></i></div>
        <small v-if="activeFloatingTask.status === 'QUEUED'">优先级 {{ priorityText(activeFloatingTaskPriority) }}</small>
        <small v-else>预计剩余 {{ formatDuration(activeFloatingTask.estimatedRemainingSeconds) }}</small>
        <div class="sidebar-task-actions">
          <button type="button" @click="openTask(activeFloatingTask.id)">查看</button>
          <button type="button" class="danger-mini" @click="cancelTask(activeFloatingTask.id)">取消</button>
        </div>
      </section>

      <div class="sidebar-status" :class="{ offline: !serverOnline }">
        <span>系统状态</span>
        <strong>
          <component :is="serverOnline ? CheckCircle2 : X" :size="15" />
          {{ serverOnline ? '在线' : '离线' }}
        </strong>
        <small>{{ serverOnline ? `运行 ${runningSlotText} · 待处理 ${serverStatus?.queuedTaskList?.length ?? 0}` : '后端连接不可用' }}</small>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div class="page-title" aria-hidden="true"></div>
        <div v-if="user" class="top-actions">
          <button class="icon-button" :class="{ 'is-spinning': refreshingCurrent }" type="button" title="刷新当前窗口" :disabled="refreshingCurrent" @click="refreshCurrentModule"><RefreshCw :size="18" /></button>
          <button class="user-chip" type="button">
            <CircleUserRound :size="18" />
            <span>{{ user.username }}</span>
            <b>{{ user.role === 'ADMIN' ? '管理员' : '用户' }}</b>
          </button>
          <button class="icon-button" type="button" title="退出登录" @click="logout"><LogOut :size="18" /></button>
        </div>
      </header>

      <section v-if="!user" class="auth-stage">
        <div class="auth-brand-copy">
          <span>MICROGRID DISPATCH</span>
          <h1>并网型微电网调度系统</h1>
        </div>

        <section class="surface auth-panel">
          <div class="auth-head">
            <div>
              <p>Account Access</p>
              <h2>{{ authMode === 'login' ? (authRole === 'ADMIN' ? '管理员登录' : '用户登录') : '注册用户账号' }}</h2>
            </div>
            <div class="segmented">
              <button type="button" :class="{ active: authMode === 'login' }" @click="authMode = 'login'">登录</button>
              <button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'">注册</button>
            </div>
          </div>
          <form class="auth-form" @submit.prevent="submitAuth">
            <label v-if="authMode === 'login'" class="field">
              <span>登录身份</span>
              <select v-model="authRole">
                <option value="USER">用户</option>
                <option value="ADMIN">管理员</option>
              </select>
            </label>
            <label class="field">
              <span>用户名</span>
              <input v-model.trim="authForm.username" autocomplete="username" required />
            </label>
            <label class="field">
              <span>密码</span>
              <input v-model="authForm.password" type="password" autocomplete="current-password" required />
            </label>
            <label class="field">
              <span>验证码</span>
              <div class="captcha-row">
                <input v-model.trim="authForm.captcha" inputmode="numeric" maxlength="4" required />
                <button type="button" class="captcha-code" @click="refreshCaptcha">{{ captchaText }}</button>
                <button type="button" class="icon-button" title="刷新验证码" @click="refreshCaptcha"><RefreshCw :size="18" /></button>
              </div>
            </label>
            <button class="primary-button wide auth-submit" :disabled="loading">{{ loading ? '处理中' : authMode === 'login' ? '登录系统' : '创建账号' }}</button>
          </form>
        </section>

        <div v-if="loginTransition && loginTransitionReason === 'login'" class="login-transition" aria-live="polite">
          <div class="login-transition-panel">
            <span></span>
            <strong>正在进入调度系统</strong>
            <em>校验身份 · 建立会话 · 加载控制台</em>
          </div>
        </div>
      </section>

      <template v-else>
        <template v-if="activeModule === 'server'">
          <section class="surface server-status-view">
            <div class="section-head">
              <div>
                <p>Server Status</p>
                <h2>
                  服务器运行状态
                  <span class="server-time">当前服务器时间 {{ serverOnline ? formatDate(serverStatus?.serverTime || serverStatusFetchedAt) : '--' }}</span>
                </h2>
              </div>
              <div class="section-actions">
                <span class="provider-chip">阿里云服务器提供云计算服务</span>
                <button class="ghost-button" type="button" @click="loadServerStatus"><RefreshCw :size="17" /> 刷新</button>
              </div>
            </div>

            <div class="desktop-panel" :class="{ expanded: desktopScreenshotExpanded }">
              <button type="button" class="desktop-toggle" @click="toggleDesktopScreenshot">
                <Monitor :size="18" />
                <div>
                  <span>Desktop Screenshot</span>
                  <strong>服务器桌面截图</strong>
                </div>
                <b>{{ desktopScreenshotExpanded ? '收起' : '展开' }}</b>
              </button>
              <div v-if="desktopScreenshotExpanded" class="desktop-body">
                <div class="desktop-actions">
                  <button class="ghost-button" type="button" :disabled="desktopScreenshotLoading" @click="loadDesktopScreenshot()">
                    <RefreshCw :size="17" /> {{ desktopScreenshotLoading ? '刷新中' : '刷新截图' }}
                  </button>
                </div>
                <button
                  type="button"
                  class="desktop-frame"
                  :class="{ clickable: desktopScreenshotUrl }"
                  :disabled="!desktopScreenshotUrl"
                  @click="openDesktopScreenshotPreview"
                >
                  <img v-if="desktopScreenshotUrl" :src="desktopScreenshotUrl" alt="服务器桌面截图" />
                  <span v-else-if="desktopScreenshotLoading">正在获取桌面截图...</span>
                  <span v-else>{{ desktopScreenshotError || '暂无桌面截图' }}</span>
                </button>
              </div>
            </div>

            <div class="server-metric-grid">
              <article class="server-chart-card">
                <div class="server-chart-head">
                  <div>
                    <span>CPU 状态</span>
                    <strong>{{ formatNumber(serverStatus?.cpuLoadPercent, 1) }}%</strong>
                    <small>阿里云 AMD EPYC 通用算力型处理器 8vCPU · 最近 60 秒记录</small>
                  </div>
                  <Cpu :size="22" />
                </div>
                <div class="metric-chart" @mousemove="chartMove" @mouseleave="chartLeave">
                  <svg :viewBox="`0 0 ${cpuHistoryChart.width} ${cpuHistoryChart.height}`" aria-label="CPU 60 秒曲线">
                    <line v-for="tick in cpuHistoryChart.yTicks" :key="`cpu-y-${tick.value}`" x1="54" :y1="tick.y" x2="976" :y2="tick.y" class="pro-grid-line" />
                    <line v-for="tick in cpuHistoryChart.xTicks" :key="`cpu-x-${tick.secondsAgo}`" :x1="tick.x" y1="24" :x2="tick.x" y2="218" class="pro-grid-line" />
                    <line x1="54" y1="24" x2="54" y2="218" class="pro-axis-line" />
                    <line x1="54" y1="218" x2="976" y2="218" class="pro-axis-line" />
                    <polyline :points="cpuHistoryChart.points" class="pro-line" :style="{ stroke: cpuHistoryChart.color }" />
                    <circle v-for="point in cpuHistoryChart.markers" :key="`cpu-${point.x}-${point.y}`" :cx="point.x" :cy="point.y" r="3.7" class="pro-point" :style="{ fill: cpuHistoryChart.color }" />
                    <text v-for="tick in cpuHistoryChart.yTicks" :key="`cpu-label-${tick.value}`" x="42" :y="tick.y + 5" class="pro-axis-text y-left">{{ tick.value }}%</text>
                    <text v-for="tick in cpuHistoryChart.xTicks" :key="`cpu-tick-${tick.secondsAgo}`" :x="tick.x" y="244" class="pro-axis-text middle">{{ tick.label }}</text>
                  </svg>
                  <div class="chart-hit-layer metric-hit-layer">
                    <button
                      v-for="(point, index) in cpuHistoryChart.markers"
                      :key="`cpu-hit-${index}`"
                      type="button"
                      :style="{ left: `${point.x / cpuHistoryChart.width * 100}%` }"
                      @mouseenter="metricHover($event, cpuHistoryChart, index, 'CPU')"
                    ></button>
                  </div>
                </div>
              </article>

              <article class="server-chart-card">
                <div class="server-chart-head">
                  <div>
                    <span>内存状态</span>
                    <strong>{{ formatNumber(serverStatus?.memoryUsedPercent, 1) }}%</strong>
                    <small>{{ serverStatus?.memoryUsedMb || 0 }} / {{ serverStatus?.memoryMaxMb || 0 }} MB · 最近 60 秒</small>
                  </div>
                  <HardDrive :size="22" />
                </div>
                <div class="metric-chart" @mousemove="chartMove" @mouseleave="chartLeave">
                  <svg :viewBox="`0 0 ${memoryHistoryChart.width} ${memoryHistoryChart.height}`" aria-label="内存 60 秒曲线">
                    <line v-for="tick in memoryHistoryChart.yTicks" :key="`memory-y-${tick.value}`" x1="54" :y1="tick.y" x2="976" :y2="tick.y" class="pro-grid-line" />
                    <line v-for="tick in memoryHistoryChart.xTicks" :key="`memory-x-${tick.secondsAgo}`" :x1="tick.x" y1="24" :x2="tick.x" y2="218" class="pro-grid-line" />
                    <line x1="54" y1="24" x2="54" y2="218" class="pro-axis-line" />
                    <line x1="54" y1="218" x2="976" y2="218" class="pro-axis-line" />
                    <polyline :points="memoryHistoryChart.points" class="pro-line" :style="{ stroke: memoryHistoryChart.color }" />
                    <circle v-for="point in memoryHistoryChart.markers" :key="`memory-${point.x}-${point.y}`" :cx="point.x" :cy="point.y" r="3.7" class="pro-point" :style="{ fill: memoryHistoryChart.color }" />
                    <text v-for="tick in memoryHistoryChart.yTicks" :key="`memory-label-${tick.value}`" x="42" :y="tick.y + 5" class="pro-axis-text y-left">{{ tick.value }}%</text>
                    <text v-for="tick in memoryHistoryChart.xTicks" :key="`memory-tick-${tick.secondsAgo}`" :x="tick.x" y="244" class="pro-axis-text middle">{{ tick.label }}</text>
                  </svg>
                  <div class="chart-hit-layer metric-hit-layer">
                    <button
                      v-for="(point, index) in memoryHistoryChart.markers"
                      :key="`memory-hit-${index}`"
                      type="button"
                      :style="{ left: `${point.x / memoryHistoryChart.width * 100}%` }"
                      @mouseenter="metricHover($event, memoryHistoryChart, index, '内存')"
                    ></button>
                  </div>
                </div>
              </article>
            </div>

            <section class="server-task-board">
              <div class="server-task-head">
                <div>
                  <p>Dispatch Queue</p>
                  <h2>调度任务列表</h2>
                </div>
                <strong>{{ runningSlotText }}</strong>
              </div>

              <div class="server-task-section">
                <div class="queue-title">
                  <strong>正在执行的调度任务</strong>
                </div>
                <div class="server-task-list">
                  <span v-if="!serverStatus?.runningTaskList?.length" class="empty-inline">当前没有运行中的调度任务</span>
                  <article
                    v-for="task in serverStatus?.runningTaskList || []"
                    :key="task.id"
                    class="server-task-row"
                    :class="{ mine: task.mine }"
                  >
                    <span class="task-main">
                      <strong>{{ task.name }}</strong>
                      <small>{{ task.username }} · {{ formatDate(task.createdAt) }}</small>
                    </span>
                    <span v-if="task.mine" class="mine-pill">我的任务</span>
                    <span class="task-progress">{{ task.progress }}%</span>
                    <button v-if="task.mine" class="text-button" type="button" @click="openTask(task.id)">查看</button>
                    <button v-if="task.mine" class="text-button danger-text" type="button" @click="cancelTask(task.id)">取消</button>
                  </article>
                </div>
              </div>

              <div class="server-task-section">
                <div class="queue-title">
                  <strong>待处理区</strong>
                  <span>服务器空闲后按动态优先级自动开始</span>
                </div>
                <div class="server-task-list">
                  <span v-if="!serverStatus?.queuedTaskList?.length" class="empty-inline">当前没有待处理的调度任务</span>
                  <article
                    v-for="(task, index) in serverStatus?.queuedTaskList || []"
                    :key="task.id"
                    class="server-task-row queued"
                    :class="{ mine: task.mine }"
                  >
                    <span class="queue-index">#{{ index + 1 }}</span>
                    <span class="task-main">
                      <strong>{{ task.name }}</strong>
                      <small>{{ task.username }} · {{ formatDate(task.createdAt) }}</small>
                    </span>
                    <span v-if="task.mine" class="mine-pill">我的任务</span>
                    <span class="priority-pill" :class="priorityClass(task.priority)">{{ priorityText(task.priority) }}</span>
                    <button v-if="task.mine" class="text-button" type="button" @click="openTask(task.id)">查看</button>
                    <button v-if="task.mine" class="text-button danger-text" type="button" @click="cancelTask(task.id)">取消</button>
                  </article>
                </div>
              </div>
            </section>
          </section>
        </template>

        <div v-if="activeModule === 'overview'" class="status-strip">
          <article v-for="item in kpis" :key="item.label" class="kpi" :class="item.tone">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}<small>{{ item.unit }}</small></strong>
            <em>{{ item.trend }}</em>
          </article>
        </div>

        <template v-if="activeModule === 'overview'">
          <section class="overview-grid">
            <div class="schedule-board surface">
              <div class="section-head">
                <div>
                  <p>{{ selectedTask ? '任务调度计划' : chartTask ? '上一次调度结果' : '数据预览计划' }}</p>
                  <h2>{{ chartTask?.name || '暂无历史调度结果' }}</h2>
                </div>
                <div class="section-actions">
                  <button
                    v-if="chartTask?.status === 'COMPLETED'"
                    class="ghost-button"
                    type="button"
                    @click="downloadResult('report.pdf')"
                  ><FileText :size="17" /> 任务报告 PDF</button>
                  <button class="primary-button" type="button" @click="activeModule = 'planner'"><Play :size="17" /> 新建任务</button>
                </div>
              </div>
              <div v-if="chartTask" class="task-result-strip">
                <span>状态：<b>{{ statusText(chartTask.status) }}</b></span>
                <span>进度：<b>{{ chartTask.progress }}%</b></span>
                <span>文件：<b>{{ chartTask.originalFilename }}</b></span>
              </div>
              <div v-if="chartTask?.status === 'COMPLETED'" class="result-toolbar">
                <div class="result-actions plan-actions">
                  <button
                    v-for="option in solutionOptions"
                    :key="option.key"
                    class="plan-choice"
                    :class="{ active: activeSolutionOption?.key === option.key }"
                    type="button"
                    @click="selectedOptionKey = option.key"
                  >
                    <strong>{{ option.label }}</strong>
                    <span>{{ economicText(option.economicCost) }} / 环境 {{ formatNumber(option.environmentCost, 0) }}</span>
                  </button>
                </div>
                <div class="result-actions download-actions">
                  <button class="ghost-button result-download" type="button" :disabled="!selectedPlanCsv" @click="downloadResult(selectedPlanCsv)"><Download :size="17" /> 当前方案 CSV</button>
                  <button class="ghost-button result-download" type="button" @click="downloadResult('pareto_front.csv')"><Download :size="17" /> Pareto CSV</button>
                  <button class="ghost-button result-download" type="button" @click="downloadResult('convergence.csv')"><Download :size="17" /> 收敛 CSV</button>
                </div>
              </div>
              <div v-if="chartTask?.status === 'COMPLETED' && paretoOptions.length" class="pareto-panel">
                <div class="pareto-head">
                  <div>
                    <p>Pareto Front</p>
                    <h3>Pareto 前沿方案</h3>
                  </div>
                  <strong>{{ activeSolutionOption?.label }}</strong>
                </div>
                <div class="pro-chart pareto-chart" @mousemove="chartMove" @mouseleave="chartLeave">
                  <svg :viewBox="`0 0 ${paretoChart.width} ${paretoChart.height}`" aria-label="Pareto 前沿方案">
                    <line v-for="tick in paretoChart.yTicks" :key="`pareto-y-${tick.value}`" x1="72" :y1="tick.y" x2="966" :y2="tick.y" class="pro-grid-line" />
                    <line v-for="tick in paretoChart.xTicks" :key="`pareto-x-${tick.value}`" :x1="tick.x" y1="30" :x2="tick.x" y2="292" class="pro-grid-line" />
                    <line x1="72" y1="30" x2="72" y2="292" class="pro-axis-line" />
                    <line x1="72" y1="292" x2="966" y2="292" class="pro-axis-line" />
                    <polyline :points="paretoChart.linePoints" class="pro-line pareto-line" />
                    <g
                      v-for="point in paretoChart.markers"
                      :key="point.key"
                      class="pareto-point-group"
                      :class="{ active: point.active, compromise: point.isCompromise, economic: point.isEconomicBest, environment: point.isEnvironmentBest }"
                      @click="selectParetoPoint(point)"
                      @mouseenter="paretoHover($event, point)"
                    >
                      <circle :cx="point.x" :cy="point.y" r="10" class="pareto-hit-point" />
                      <circle :cx="point.x" :cy="point.y" :r="point.active ? 7 : 5" class="pareto-point" />
                    </g>
                    <text x="18" y="176" class="pro-axis-title y">环境成本</text>
                    <text x="500" y="334" class="pro-axis-title">经济成本 / 元</text>
                    <text v-for="tick in paretoChart.yTicks" :key="`pareto-y-label-${tick.value}`" x="58" :y="tick.y + 5" class="pro-axis-text y-left">{{ formatNumber(tick.value, 1) }}</text>
                    <text v-for="tick in paretoChart.xTicks" :key="`pareto-x-label-${tick.value}`" :x="tick.x" y="316" class="pro-axis-text middle">{{ formatNumber(tick.value, 1) }}</text>
                  </svg>
                  <div class="chart-legend">
                    <span><i class="pareto-dot"></i>Pareto 方案</span>
                    <span><i class="pareto-dot active"></i>当前方案</span>
                    <span><i class="pareto-dot compromise"></i>折中解</span>
                    <span><i class="pareto-dot economic"></i>经济最优</span>
                    <span><i class="pareto-dot environment"></i>环境最优</span>
                  </div>
                </div>
                <div class="pareto-detail">
                  <span>经济成本 <b>{{ formatNumber(activeSolutionOption?.economicCost, 2) }}</b></span>
                  <span>环境成本 <b>{{ formatNumber(activeSolutionOption?.environmentCost, 2) }}</b></span>
                  <span>可再生能源利用率 <b>{{ formatPercent(activeSolutionOption?.renewableUtilizationRate) }}</b></span>
                  <span>最终 SOC <b>{{ formatPercent(activeSolutionOption?.finalSoc) }}</b></span>
                </div>
              </div>
              <div v-if="!curveRows.length" class="overview-empty">
                <strong>暂无可预览的历史调度图表</strong>
                <span>该用户还没有已完成任务，或历史记录已被删除。新建任务完成后，这里会显示上一次结果图。</span>
              </div>

              <template v-else>
                <div class="chart-block-title">功率平衡</div>
                <div class="pro-chart result-chart">
                  <svg :viewBox="`0 0 ${balanceChart.width} ${balanceChart.height}`" aria-label="功率平衡" @mousemove="chartMove" @mouseleave="chartLeave">
                    <line v-for="y in balanceChart.yLines" :key="`balance-y-${y}`" x1="64" :y1="y" x2="966" :y2="y" class="pro-grid-line" />
                    <line v-for="tick in balanceChart.xTicks" :key="`balance-x-${tick.hour}`" :x1="tick.x" y1="30" :x2="tick.x" y2="424" class="pro-grid-line" />
                    <line x1="64" y1="30" x2="64" y2="424" class="pro-axis-line" />
                    <line x1="64" :y1="balanceChart.zeroY" x2="966" :y2="balanceChart.zeroY" class="pro-axis-line strong" />
                    <rect v-for="bar in balanceChart.bars" :key="bar.key" :x="bar.x" :y="bar.y" :width="bar.width" :height="bar.height" rx="2" class="balance-bar" :style="{ fill: bar.color }" />
                    <polyline :points="balanceChart.loadPoints" class="pro-line load-line" />
                    <circle v-for="point in balanceChart.loadMarkers" :key="`load-${point.x}`" :cx="point.x" :cy="point.y" r="4" class="pro-point load-point" />
                    <text x="12" y="232" class="pro-axis-title y">功率 / kW</text>
                    <text x="500" y="466" class="pro-axis-title">时间 / h</text>
                    <text v-for="tick in balanceChart.yTicks" :key="`balance-label-${tick.value}`" x="50" :y="tick.y + 5" class="pro-axis-text y-left">{{ formatNumber(tick.value, 0) }}</text>
                    <text v-for="tick in balanceChart.xTicks" :key="`balance-tick-${tick.hour}`" :x="tick.x" y="448" class="pro-axis-text middle">{{ tick.hour }}</text>
                    <rect
                      v-for="(zone, index) in balanceChart.hitZones"
                      :key="zone.key"
                      :x="zone.x"
                      :y="zone.y"
                      :width="zone.width"
                      :height="zone.height"
                      class="svg-hit-zone"
                      :class="{ active: activeChartHover.chart === 'balance' && activeChartHover.index === index }"
                      @mouseenter="balanceHover($event, curveRows, index)"
                    />
                  </svg>
                  <div class="chart-legend">
                    <span><i class="load-dot"></i>负荷</span>
                    <span><i class="wind-dot"></i>风机</span>
                    <span><i class="pv-dot"></i>光伏</span>
                    <span><i style="background:#9c7a52"></i>微型燃气轮机</span>
                    <span><i style="background:#59a14f"></i>主网购电</span>
                    <span><i style="background:#e15759"></i>储能放电</span>
                    <span><i style="background:#af7aa1"></i>储能充电</span>
                    <span><i style="background:#76b7b2"></i>主网售电</span>
                  </div>
                </div>

                <div class="chart-block-title secondary">储能功率与荷电状态</div>
                <div class="pro-chart result-chart storage-result-chart">
                  <svg :viewBox="`0 0 ${storageChart.width} ${storageChart.height}`" aria-label="储能功率与荷电状态" @mousemove="chartMove" @mouseleave="chartLeave">
                    <line v-for="y in storageChart.yLines" :key="`storage-y-${y}`" x1="64" :y1="y" x2="942" :y2="y" class="pro-grid-line" />
                    <line v-for="tick in storageChart.xTicks" :key="`storage-x-${tick.hour}`" :x1="tick.x" y1="28" :x2="tick.x" y2="378" class="pro-grid-line" />
                    <line x1="64" y1="28" x2="64" y2="378" class="pro-axis-line" />
                    <line x1="942" y1="28" x2="942" y2="378" class="pro-axis-line" />
                    <line x1="64" :y1="storageChart.zeroY" x2="942" :y2="storageChart.zeroY" class="pro-axis-line strong" />
                    <rect v-for="bar in storageChart.dischargeBars" :key="bar.key" :x="bar.x" :y="bar.y" :width="bar.width" :height="bar.height" rx="2" class="storage-bar discharge" />
                    <rect v-for="bar in storageChart.chargeBars" :key="bar.key" :x="bar.x" :y="bar.y" :width="bar.width" :height="bar.height" rx="2" class="storage-bar charge" />
                    <polyline :points="storageChart.netPoints" class="pro-line storage-net-line" />
                    <polyline :points="storageChart.socPoints" class="pro-line soc-line" />
                    <circle v-for="point in storageChart.netMarkers" :key="`net-${point.x}`" :cx="point.x" :cy="point.y" r="3.8" class="pro-point storage-net-point" />
                    <circle v-for="point in storageChart.socMarkers" :key="`soc-${point.x}`" :cx="point.x" :cy="point.y" r="3.8" class="pro-point soc-point" />
                    <text x="12" y="214" class="pro-axis-title y">储能功率 / kW</text>
                    <text x="992" y="214" class="pro-axis-title y">荷电状态</text>
                    <text x="500" y="416" class="pro-axis-title">时间 / h</text>
                    <text v-for="tick in storageChart.yTicks" :key="`storage-label-${tick.value}`" x="50" :y="tick.y + 5" class="pro-axis-text y-left">{{ formatNumber(tick.value, 0) }}</text>
                    <text v-for="tick in storageChart.socTicks" :key="`storage-soc-label-${tick.value}`" x="958" :y="tick.y + 5" class="pro-axis-text y-right">{{ tick.label }}</text>
                    <text v-for="tick in storageChart.xTicks" :key="`storage-tick-${tick.hour}`" :x="tick.x" y="398" class="pro-axis-text middle">{{ tick.hour }}</text>
                    <rect
                      v-for="(zone, index) in storageChart.hitZones"
                      :key="zone.key"
                      :x="zone.x"
                      :y="zone.y"
                      :width="zone.width"
                      :height="zone.height"
                      class="svg-hit-zone"
                      :class="{ active: activeChartHover.chart === 'storage' && activeChartHover.index === index }"
                      @mouseenter="storageHover($event, curveRows, index)"
                    />
                  </svg>
                  <div class="chart-legend">
                    <span><i class="storage-net-dot"></i>储能净功率</span>
                    <span><i style="background:#e15759"></i>储能放电</span>
                    <span><i style="background:#af7aa1"></i>储能充电</span>
                    <span><i class="soc-dot"></i>荷电状态</span>
                  </div>
                </div>
              </template>

            </div>

            <aside class="side-column">
              <section class="surface side-panel">
                <div class="side-panel-section">
                  <div class="section-head compact-head">
                    <div>
                      <p>资源状态</p>
                      <h2>可用容量</h2>
                    </div>
                  </div>
                  <div class="resource-list">
                    <article v-for="item in resourceCards" :key="item.label" :class="`resource ${item.tone}`">
                      <component :is="item.icon" :size="19" />
                      <div>
                        <span>{{ item.label }}</span>
                        <strong>{{ item.value }}</strong>
                        <small>{{ item.detail }}</small>
                        <i><b :style="{ width: `${item.fill}%` }"></b></i>
                      </div>
                    </article>
                  </div>
                </div>

                <div class="side-panel-section alert-section">
                  <div class="section-head compact-head">
                    <div>
                      <p>风险提示</p>
                      <h2>约束告警</h2>
                    </div>
                  </div>
                  <div class="alert-list">
                    <article v-for="item in alerts" :key="item.title" class="alert-item">
                      <span>{{ item.level }}</span>
                      <div>
                        <strong>{{ item.title }}</strong>
                        <p>{{ item.detail }}</p>
                      </div>
                    </article>
                  </div>
                </div>
              </section>
            </aside>
          </section>
        </template>

        <template v-else-if="activeModule === 'planner'">
          <section class="planner-layout">
            <div class="surface form-surface">
              <div class="section-head compact-head">
                <div>
                  <p>当前任务参数</p>
                  <h2>任务参数</h2>
                </div>
              </div>
              <label class="field">
                <span>任务名称</span>
                <input v-model="uploadForm.name" />
              </label>
              <label class="field">
                <span>参数来源</span>
                <select v-model="previousParamSet">
                  <option v-for="item in previousParamOptions" :key="item.key" :value="item.key">{{ item.label }}</option>
                </select>
                <small class="field-note">{{ previousParamOptions.find((item) => item.key === previousParamSet)?.detail }}</small>
              </label>
              <div class="field">
                <div class="field-row">
                  <span>算法参数</span>
                </div>
                <button class="ghost-button" type="button" @click="showTaskAlgorithmOptions = !showTaskAlgorithmOptions">
                  {{ showTaskAlgorithmOptions ? '收起可选算法参数' : '展开可选算法参数' }}
                </button>
                <small class="field-note">默认使用系统当前算法参数，建议不熟悉算法的不要更改。</small>
              </div>
              <div v-if="showTaskAlgorithmOptions" class="setting-grid compact-setting-grid">
                <label v-for="[key, label] in algorithmSettingFields" :key="key" class="field">
                  <span>{{ label }}</span>
                  <input
                    v-model.number="taskAlgorithmSettings[key]"
                    type="number"
                    :min="algorithmFieldMin(key)"
                    :max="algorithmFieldMax(key)"
                    :step="algorithmFieldStep(key)"
                  />
                </label>
              </div>
              <div class="setting-grid compact-setting-grid">
                <label v-for="[key, label] in settingFields" :key="key" class="field">
                  <span>{{ label }}</span>
                  <input v-model.number="taskSettings[key]" type="number" step="0.01" />
                </label>
              </div>
              <p class="permission-note">普通用户仅调整当前任务参数；提交时参数会随本次任务发送到后端，不会改系统默认值。</p>
              <button class="primary-button wide" type="button" :disabled="loading || previewLoading" @click="confirmUploadTask"><Play :size="17" /> 提交计算</button>
            </div>

            <div class="surface upload-surface">
              <div class="section-head">
                <div>
                  <p>新建任务</p>
                  <h2>上传预测数据并生成调度任务</h2>
                </div>
                <button class="ghost-button" type="button" @click="downloadTemplate"><FileSpreadsheet :size="17" /> 下载模板</button>
              </div>
              <input ref="fileInputRef" class="file-input" type="file" accept=".csv,.xlsx,.xlsm,.xls" @change="onFileChange" />
              <div class="upload-zone" :class="{ imported: hasImportedProfile }" @click="openFilePicker">
                <CloudUpload :size="34" />
                <strong>{{ hasImportedProfile ? selectedDataFileName : '选择 CSV / XLSX 预测文件' }}</strong>
                <span>{{ hasImportedProfile ? '后端已完成解析，下方已生成导入后曲线。' : '导入前会先显示系统模板曲线；导入后会用后端解析结果重新绘图。' }}</span>
                <button class="primary-button" type="button" @click.stop="openFilePicker"><UploadCloud :size="17" /> 选择文件</button>
                <button v-if="uploadForm.file" class="ghost-button" type="button" @click.stop="clearSelectedFile">清除文件</button>
              </div>
              <div class="scenario-library">
                <div class="scenario-library-head">
                  <div>
                    <strong>工况模板</strong>
                    <span>{{ activeScenario.name }} · {{ activeScenario.detail }}</span>
                  </div>
                </div>
                <div class="scenario-grid">
                  <button
                    v-for="scenario in dispatchScenarios"
                    :key="scenario.key"
                    class="scenario-card"
                    :class="{ active: selectedScenarioKey === scenario.key }"
                    type="button"
                    @click="applyScenario(scenario.key)"
                  >
                    <small>{{ scenario.tag }}</small>
                    <strong>{{ scenario.name }}</strong>
                    <span>{{ scenario.detail }}</span>
                  </button>
                </div>
              </div>
              <div v-if="previewLoading" class="upload-validation info">正在解析数据文件...</div>
              <div v-if="uploadValidationErrors.length" class="upload-validation error">
                <span v-for="error in uploadValidationErrors" :key="error">{{ error }}</span>
              </div>
            </div>

            <div class="surface chart-surface merged-resource-panel">
              <div class="section-head">
                <div>
                  <p>数据预览</p>
                  <h2>预测数据曲线 <span class="title-context">{{ currentPreviewName }}</span></h2>
                </div>
              </div>
              <div class="preview-chart-stack">
                <div class="preview-chart-box">
                  <strong>风电、光伏及负荷预测曲线</strong>
                  <div class="pro-chart preview-pro-chart">
                    <svg :viewBox="`0 0 ${profilePreviewChart.width} ${profilePreviewChart.height}`" aria-label="风电光伏负荷预测曲线">
                      <line v-for="y in profilePreviewChart.yLines" :key="`profile-y-${y}`" :x1="profilePreviewChart.plotLeft" :y1="y" :x2="profilePreviewChart.plotRight" :y2="y" class="pro-grid-line" />
                      <line v-for="tick in profilePreviewChart.xTicks" :key="`profile-x-${tick.hour}`" :x1="tick.x" :y1="profilePreviewChart.plotTop" :x2="tick.x" :y2="profilePreviewChart.plotBottom" class="pro-grid-line" />
                      <line :x1="profilePreviewChart.plotLeft" :y1="profilePreviewChart.plotTop" :x2="profilePreviewChart.plotLeft" :y2="profilePreviewChart.plotBottom" class="pro-axis-line" />
                      <line :x1="profilePreviewChart.plotLeft" :y1="profilePreviewChart.plotBottom" :x2="profilePreviewChart.plotRight" :y2="profilePreviewChart.plotBottom" class="pro-axis-line" />
                      <text x="18" :y="profilePreviewChart.yTitleY" class="pro-axis-title y">{{ profilePreviewChart.yTitle }}</text>
                      <text x="500" :y="profilePreviewChart.xTitleY" class="pro-axis-title">时间 / h</text>
                      <text v-for="(label, index) in profilePreviewChart.yLabels" :key="`profile-label-${index}`" x="38" :y="profilePreviewChart.yLines[index] + 5" class="pro-axis-text">{{ formatNumber(label, profilePreviewChart.yDigits) }}</text>
                      <text v-for="tick in profilePreviewChart.xTicks" :key="`profile-tick-${tick.hour}`" :x="tick.x" :y="profilePreviewChart.plotBottom + 24" class="pro-axis-text middle">{{ tick.hour }}</text>
                      <polyline v-for="line in profilePreviewChart.lines" :key="line.key" :points="line.points" class="pro-line" :style="{ stroke: line.color }" />
                      <template v-for="line in profilePreviewChart.lines" :key="`profile-markers-${line.key}`">
                        <circle v-for="point in line.markers" :key="`${line.key}-${point.x}`" :cx="point.x" :cy="point.y" r="4.2" class="pro-point" :style="{ fill: line.color }" />
                      </template>
                    </svg>
                    <div class="chart-hit-layer pro-hit-layer" @mousemove="chartMove" @mouseleave="chartLeave">
                      <button
                        v-for="(row, index) in activeProfileRows"
                        :key="`profile-${index}`"
                        type="button"
                        :class="{ active: activeChartHover.chart === 'profile-preview' && activeChartHover.index === index }"
                        :style="{ left: `${Math.min(100, n(row.hour) / 24 * 100)}%` }"
                        @mouseenter="chartHover($event, activeProfileRows, index, 'profile-preview')"
                      ></button>
                    </div>
                  </div>
                  <div class="chart-legend">
                    <span><i class="load-dot"></i>负荷</span>
                    <span><i class="pv-dot"></i>光伏</span>
                    <span><i class="wind-dot"></i>风机</span>
                  </div>
                </div>
                <div class="preview-chart-box">
                  <strong>分时购售电价格曲线</strong>
                  <div class="pro-chart preview-pro-chart">
                    <svg :viewBox="`0 0 ${pricePreviewChart.width} ${pricePreviewChart.height}`" aria-label="分时购售电价格曲线">
                      <line v-for="y in pricePreviewChart.yLines" :key="`price-y-${y}`" :x1="pricePreviewChart.plotLeft" :y1="y" :x2="pricePreviewChart.plotRight" :y2="y" class="pro-grid-line" />
                      <line v-for="tick in pricePreviewChart.xTicks" :key="`price-x-${tick.hour}`" :x1="tick.x" :y1="pricePreviewChart.plotTop" :x2="tick.x" :y2="pricePreviewChart.plotBottom" class="pro-grid-line" />
                      <line :x1="pricePreviewChart.plotLeft" :y1="pricePreviewChart.plotTop" :x2="pricePreviewChart.plotLeft" :y2="pricePreviewChart.plotBottom" class="pro-axis-line" />
                      <line :x1="pricePreviewChart.plotLeft" :y1="pricePreviewChart.plotBottom" :x2="pricePreviewChart.plotRight" :y2="pricePreviewChart.plotBottom" class="pro-axis-line" />
                      <text x="18" :y="pricePreviewChart.yTitleY" class="pro-axis-title y">{{ pricePreviewChart.yTitle }}</text>
                      <text x="500" :y="pricePreviewChart.xTitleY" class="pro-axis-title">时间 / h</text>
                      <text v-for="(label, index) in pricePreviewChart.yLabels" :key="`price-label-${index}`" x="38" :y="pricePreviewChart.yLines[index] + 5" class="pro-axis-text">{{ formatNumber(label, pricePreviewChart.yDigits) }}</text>
                      <text v-for="tick in pricePreviewChart.xTicks" :key="`price-tick-${tick.hour}`" :x="tick.x" :y="pricePreviewChart.plotBottom + 24" class="pro-axis-text middle">{{ tick.hour }}</text>
                      <polyline v-for="line in pricePreviewChart.lines" :key="line.key" :points="line.points" class="pro-line" :style="{ stroke: line.color }" />
                      <template v-for="line in pricePreviewChart.lines" :key="`price-markers-${line.key}`">
                        <circle v-for="point in line.markers" :key="`${line.key}-${point.x}`" :cx="point.x" :cy="point.y" r="4.2" class="pro-point" :style="{ fill: line.color }" />
                      </template>
                    </svg>
                    <div class="chart-hit-layer pro-hit-layer" @mousemove="chartMove" @mouseleave="chartLeave">
                      <button
                        v-for="(row, index) in activeProfileRows"
                        :key="`price-${index}`"
                        type="button"
                        :class="{ active: activeChartHover.chart === 'price-preview' && activeChartHover.index === index }"
                        :style="{ left: `${Math.min(100, n(row.hour) / 24 * 100)}%` }"
                        @mouseenter="priceHover($event, activeProfileRows, index)"
                      ></button>
                    </div>
                  </div>
                  <div class="chart-legend">
                    <span><i style="background:#4e79a7;"></i>购电价</span>
                    <span><i style="background:#f2a65a;"></i>售电价</span>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </template>

        <template v-else-if="activeModule === 'history'">
          <section class="surface">
            <div class="section-head">
              <div>
                <p>任务记录</p>
                <h2>运行历史与结果文件</h2>
              </div>
              <div class="section-actions">
                <button class="ghost-button" type="button" @click="loadTasks"><RefreshCw :size="17" /> 刷新</button>
                <button class="danger-button" type="button" :disabled="!selectedTaskIds.length" @click="deleteSelectedTasks">删除选中</button>
              </div>
            </div>
            <div class="task-table">
              <table>
                <thead>
                  <tr>
                    <th></th>
                    <th>任务编号</th>
                    <th>任务名称</th>
                    <th>状态</th>
                    <th>进度</th>
                    <th>数据文件</th>
                    <th>创建时间</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="task in tasks" :key="task.id" :class="{ selected: selectedTask?.id === task.id }">
                    <td><input type="checkbox" :checked="selectedTaskIds.includes(task.id)" @change="toggleTaskSelection(task.id, $event.target.checked)" /></td>
                    <td>{{ task.id }}</td>
                    <td>{{ task.name }}</td>
                    <td><span class="status-pill" :class="taskClass(task.status)">{{ statusText(task.status) }}</span></td>
                    <td><span class="table-progress"><b :style="{ width: `${task.progress}%` }"></b></span></td>
                    <td>{{ task.originalFilename }}</td>
                    <td>{{ formatDate(task.createdAt) }}</td>
                    <td><button class="text-button" type="button" @click="openTask(task.id)">查看</button></td>
                  </tr>
                  <tr v-if="!tasks.length"><td colspan="8" class="empty">暂无任务记录</td></tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>

        <template v-else-if="activeModule === 'admin'">
          <section class="admin-grid">
            <div class="surface algorithm-settings-panel">
              <div class="section-head">
                <div>
                  <p>算法默认参数</p>
                  <h2>MOIABC 默认运行参数</h2>
                </div>
                <button class="primary-button" type="button" @click="saveAlgorithmSettings"><Cpu :size="17" /> 保存算法值</button>
              </div>
              <div class="algorithm-settings-grid">
                <label v-for="[key, label] in algorithmSettingFields" :key="key" class="field">
                  <span>{{ label }}</span>
                  <input
                    v-model.number="algorithmSettings[key]"
                    type="number"
                    :min="algorithmFieldMin(key)"
                    :max="algorithmFieldMax(key)"
                    :step="algorithmFieldStep(key)"
                  />
                </label>
              </div>
              <p class="permission-note admin-note">算法默认值会持久化到后端，并作为普通用户新建任务时的初始值；单独修改后，不会影响已创建任务。</p>
            </div>

            <div class="surface default-settings-panel">
              <div class="section-head">
                <div>
                  <p>管理员默认参数</p>
                  <h2>系统默认调度参数</h2>
                </div>
                <button class="primary-button" type="button" @click="saveDefaultSettings"><Settings :size="17" /> 保存默认值</button>
              </div>
              <div class="default-settings-grid">
                <label v-for="[key, label] in settingFields" :key="key" class="field">
                  <span>{{ label }}</span>
                  <input v-model.number="defaultSettings[key]" type="number" step="0.01" />
                </label>
              </div>
              <p class="permission-note admin-note">默认参数会持久化到后端，并作为普通用户新建任务时的初始参数；普通用户提交时仍会生成当前任务参数副本。</p>
            </div>

            <div class="surface default-settings-panel">
              <div class="section-head">
                <div>
                  <p>User Admin</p>
                  <h2>用户管理</h2>
                </div>
                <button class="ghost-button" type="button" @click="loadAdminUsers">刷新</button>
              </div>
              <div class="task-table">
                <table>
                  <thead><tr><th>用户</th><th>角色</th><th>任务数</th><th>创建时间</th><th></th></tr></thead>
                  <tbody>
                    <tr v-for="item in adminUsers" :key="item.id">
                      <td>{{ item.username }}</td>
                      <td><span class="status-pill" :class="item.role === 'ADMIN' ? 'done' : 'running'">{{ item.role === 'ADMIN' ? '管理员' : '用户' }}</span></td>
                      <td>{{ item.taskCount }}</td>
                      <td>{{ formatDate(item.createdAt) }}</td>
                      <td><button class="text-button" type="button" @click="updateUserRole(item, item.role === 'ADMIN' ? 'USER' : 'ADMIN')">{{ item.role === 'ADMIN' ? '降级为用户' : '提升为管理员' }}</button></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        </template>
      </template>
    </section>

    <div v-if="desktopScreenshotPreviewOpen" class="screenshot-lightbox" @click.self="desktopScreenshotPreviewOpen = false">
      <div class="screenshot-lightbox-panel">
        <button type="button" class="icon-button screenshot-lightbox-close" title="关闭" @click="desktopScreenshotPreviewOpen = false"><X :size="18" /></button>
        <img :src="desktopScreenshotUrl" alt="服务器桌面截图放大预览" />
      </div>
    </div>

    <div v-if="showConfirm" class="modal-backdrop" @click.self="showConfirm = false">
      <section class="modal-panel">
        <div class="section-head">
          <div>
            <p>Confirm</p>
            <h2>确认生成调度任务</h2>
          </div>
        </div>
        <div class="confirm-list">
          <div><span>任务名称</span><strong>{{ uploadForm.name }}</strong></div>
          <div><span>数据文件</span><strong>{{ selectedDataFileName }}</strong></div>
          <div><span>参数来源</span><strong>{{ previousParamOptions.find((item) => item.key === previousParamSet)?.label }}</strong></div>
          <div v-if="showTaskAlgorithmOptions"><span>算法参数</span><strong>已启用可选配置</strong></div>
        </div>
        <div class="modal-actions">
          <button class="ghost-button" type="button" @click="showConfirm = false">取消</button>
          <button class="primary-button" type="button" :disabled="loading || submittingTask" @click="uploadTask">{{ submittingTask ? '提交中' : '确认提交' }}</button>
        </div>
      </section>
    </div>

    <button v-if="sidebarOpen" class="scrim" type="button" aria-label="关闭导航" @click="sidebarOpen = false">
      <X :size="20" />
    </button>
    <p v-if="message" class="toast">{{ message }}</p>
    <div v-if="tooltip.show" class="data-tooltip" :style="{ left: `${tooltip.x}px`, top: `${tooltip.y}px` }">
      <strong>{{ tooltip.title }}</strong>
      <span v-for="line in tooltip.lines" :key="line">{{ line }}</span>
    </div>
  </main>
</template>
