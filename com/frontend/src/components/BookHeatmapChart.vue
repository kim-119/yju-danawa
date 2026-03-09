<template>
  <div class="space-y-2">
    <h4 class="text-xs font-semibold text-gray-500 uppercase tracking-wide">{{ title }}</h4>

    <div v-if="totalCount === 0" class="flex gap-1.5">
      <div
        v-for="cell in cells"
        :key="cell.label"
        class="flex-1 flex flex-col items-center gap-1"
      >
        <div
          class="w-full rounded-md border-2 border-dashed border-gray-200"
          style="height:40px"
          :title="`${cell.label}: 0명`"
        />
        <span class="text-[10px] text-gray-400 text-center leading-tight">{{ cell.label }}</span>
        <span class="text-[10px] text-gray-300 font-bold">0</span>
      </div>
    </div>

    <div v-else class="flex gap-1.5">
      <div
        v-for="cell in cells"
        :key="cell.label"
        class="flex-1 flex flex-col items-center gap-1"
      >
        <div
          class="w-full rounded-md transition-all duration-500"
          :style="{ height: '40px', backgroundColor: getColor(cell.count) }"
          :title="`${cell.label}: ${cell.count}명`"
        />
        <span class="text-[10px] text-gray-500 text-center leading-tight">{{ cell.label }}</span>
        <span class="text-[10px] font-bold" :class="cell.count > 0 ? 'text-gray-700' : 'text-gray-300'">
          {{ cell.count }}
        </span>
      </div>
    </div>

    <p v-if="totalCount === 0" class="text-xs text-gray-400 text-center py-0.5">아직 리뷰 데이터가 없습니다.</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
  baseColor: { type: String, default: '#3B4EA6' }
})

const DIFFICULTY_LABELS = { 1: '매우쉬움', 2: '쉬움', 3: '보통', 4: '어려움', 5: '매우어려움' }
const COMPLETION_LABELS = { 25: '초반포기', 50: '절반읽음', 75: '거의완독', 100: '완독' }

const cells = computed(() => {
  if (props.type === 'difficulty') {
    return [1, 2, 3, 4, 5].map(k => ({
      label: DIFFICULTY_LABELS[k],
      count: Number(props.data[k] ?? 0)
    }))
  }
  return [25, 50, 75, 100].map(k => ({
    label: COMPLETION_LABELS[k],
    count: Number(props.data[k] ?? 0)
  }))
})

const totalCount = computed(() => cells.value.reduce((s, c) => s + c.count, 0))
const maxCount   = computed(() => Math.max(...cells.value.map(c => c.count), 1))

const title = computed(() =>
  props.type === 'difficulty' ? '체감 난이도 분포' : '완독률 분포'
)

/** hex → [r, g, b] */
function hexToRgb(hex) {
  const h = hex.replace('#', '')
  return [
    parseInt(h.slice(0, 2), 16),
    parseInt(h.slice(2, 4), 16),
    parseInt(h.slice(4, 6), 16)
  ]
}

function getColor(count) {
  if (count === 0) return 'rgba(0,0,0,0.06)'
  const ratio = count / maxCount.value
  // 30% → 100% opacity → always visible
  const opacity = (0.30 + ratio * 0.70).toFixed(2)
  const [r, g, b] = hexToRgb(props.baseColor)
  return `rgba(${r},${g},${b},${opacity})`
}
</script>
