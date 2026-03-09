<template>
  <div class="bg-white rounded-xl border border-gray-200/80 shadow-sm p-4">
    <div class="flex items-center gap-2 mb-3">
      <span class="w-6 h-6 rounded bg-red-50 flex items-center justify-center flex-shrink-0">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"/>
        </svg>
      </span>
      <h3 class="font-bold text-sm text-gray-900">실시간 인기 검색어</h3>
      <span class="text-[10px] text-gray-400 ml-auto">{{ lastUpdated }}</span>
    </div>

    <div v-if="loading" class="space-y-2">
      <div v-for="n in 10" :key="n" class="skeleton h-5 rounded"></div>
    </div>

    <TransitionGroup v-else name="slide" tag="ol" class="space-y-1">
      <li
        v-for="item in items"
        :key="item.keyword"
        @click="search(item.keyword)"
        class="flex items-center gap-2.5 cursor-pointer hover:bg-gray-50 rounded-lg px-2 py-1.5 transition-colors"
      >
        <span class="w-5 text-center text-xs font-bold flex-shrink-0" :class="rankColor(item.rank)">
          {{ item.rank }}
        </span>
        <span class="text-sm text-gray-700 truncate flex-1">{{ item.keyword }}</span>
        <span class="text-[10px] text-gray-400 flex-shrink-0">{{ item.count.toLocaleString() }}회</span>
      </li>
    </TransitionGroup>

    <p v-if="!loading && items.length === 0" class="text-xs text-gray-400 text-center py-4">
      검색 데이터가 아직 없습니다.
    </p>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'

const router = useRouter()
const items = ref([])
const loading = ref(true)
const lastUpdated = ref('')

async function fetchPopular() {
  try {
    const { data } = await api.getPopularKeywords()
    // 응답: [{rank, keyword, count}, ...] — 전역 단일 키 집계 결과
    items.value = Array.isArray(data) ? data : []
    const now = new Date()
    lastUpdated.value = now.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

function search(keyword) {
  router.push({ name: 'search', query: { q: keyword } })
}

function rankColor(rank) {
  if (rank <= 3) return 'text-red-500'
  if (rank <= 6) return 'text-orange-400'
  return 'text-gray-400'
}

let interval = null

onMounted(() => {
  fetchPopular()
  interval = setInterval(fetchPopular, 30000)
})

onUnmounted(() => {
  clearInterval(interval)
})
</script>

<style scoped>
.slide-move,
.slide-enter-active,
.slide-leave-active {
  transition: all 0.35s ease;
}
.slide-enter-from {
  opacity: 0;
  transform: translateY(-6px);
}
.slide-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
.slide-leave-active {
  position: absolute;
}
</style>
