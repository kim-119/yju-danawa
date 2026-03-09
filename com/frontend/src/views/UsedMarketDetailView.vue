<template>
  <div class="page-container py-8 max-w-3xl">
    <!-- 뒤로가기 -->
    <button @click="$router.back()" class="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-800 mb-6 transition-colors">
      <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
      </svg>
      목록으로
    </button>

    <!-- 로딩 -->
    <div v-if="loading" class="space-y-4">
      <div class="skeleton h-64 rounded-xl"></div>
      <div class="skeleton h-6 rounded w-2/3"></div>
      <div class="skeleton h-4 rounded w-1/3"></div>
    </div>

    <!-- 에러 -->
    <div v-else-if="error" class="text-center py-20 text-gray-400">
      <p class="text-sm">{{ error }}</p>
      <RouterLink to="/market" class="mt-4 inline-block text-yju text-sm underline">목록으로 돌아가기</RouterLink>
    </div>

    <!-- 상세 -->
    <div v-else-if="book">
      <!-- 이미지 갤러리 -->
      <div v-if="book.imageUrls && book.imageUrls.length > 0" class="mb-6">
        <div class="aspect-video bg-gray-50 rounded-xl overflow-hidden border border-gray-200/60 mb-2">
          <img
            :src="book.imageUrls[selectedImg]"
            :alt="book.title"
            class="w-full h-full object-contain"
            @error="e => e.target.src = 'https://placehold.co/600x400?text=No+Image'"
          />
        </div>
        <div v-if="book.imageUrls.length > 1" class="flex gap-2 overflow-x-auto">
          <button
            v-for="(url, i) in book.imageUrls"
            :key="i"
            @click="selectedImg = i"
            :class="['flex-shrink-0 w-16 h-16 rounded-lg overflow-hidden border-2 transition-all',
                     selectedImg === i ? 'border-yju' : 'border-gray-200 hover:border-gray-400']"
          >
            <img :src="url" class="w-full h-full object-cover" @error="e => e.target.src = 'https://placehold.co/64x64?text=X'" />
          </button>
        </div>
      </div>
      <div v-else class="aspect-video bg-gray-50 rounded-xl border border-gray-200/60 flex items-center justify-center mb-6">
        <p class="text-gray-400 text-sm">이미지 없음</p>
      </div>

      <!-- 정보 -->
      <div class="bg-white rounded-xl border border-gray-200/60 shadow-sm p-5">
        <!-- 상태 + 학과 -->
        <div class="flex items-center gap-2 mb-3">
          <span :class="statusBadge(book.status)" class="text-xs font-semibold px-2.5 py-1 rounded-full">
            {{ statusLabel(book.status) }}
          </span>
          <span v-if="book.departmentName" class="text-xs text-gray-400 bg-gray-100 px-2.5 py-1 rounded-full">
            {{ book.departmentName }}
          </span>
        </div>

        <h1 class="text-xl font-black text-gray-900 mb-1">{{ book.title }}</h1>
        <p v-if="book.author" class="text-sm text-gray-500 mb-4">{{ book.author }}</p>

        <p class="text-2xl font-black text-yju mb-5">
          {{ book.priceWon ? book.priceWon.toLocaleString() + '원' : '가격 미정' }}
        </p>

        <div v-if="book.description" class="bg-gray-50 rounded-lg p-4 mb-5">
          <p class="text-sm text-gray-700 whitespace-pre-line leading-relaxed">{{ book.description }}</p>
        </div>

        <!-- 메타 정보 -->
        <div class="space-y-2 text-sm border-t border-gray-100 pt-4">
          <div v-if="book.isbn" class="flex gap-3">
            <span class="text-gray-400 w-16 flex-shrink-0">ISBN</span>
            <span class="text-gray-700 font-mono">{{ book.isbn }}</span>
          </div>
          <div class="flex gap-3">
            <span class="text-gray-400 w-16 flex-shrink-0">판매자</span>
            <span class="text-gray-700">{{ book.sellerUsername }}</span>
          </div>
          <div class="flex gap-3">
            <span class="text-gray-400 w-16 flex-shrink-0">등록일</span>
            <span class="text-gray-700">{{ formatDate(book.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'

const route = useRoute()
const book  = ref(null)
const loading = ref(true)
const error = ref(null)
const selectedImg = ref(0)

function statusLabel(status) {
  return { AVAILABLE: '판매중', RESERVED: '예약중', SOLD: '판매완료' }[status] ?? '판매중'
}
function statusBadge(status) {
  return {
    AVAILABLE: 'bg-green-100 text-green-700',
    RESERVED:  'bg-yellow-100 text-yellow-700',
    SOLD:      'bg-gray-100 text-gray-500',
  }[status] ?? 'bg-green-100 text-green-700'
}
function formatDate(isoStr) {
  if (!isoStr) return '-'
  return new Date(isoStr).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}

onMounted(async () => {
  try {
    const { data } = await api.getUsedBookDetail(route.params.id)
    book.value = data
  } catch {
    error.value = '게시글을 불러올 수 없습니다.'
  } finally {
    loading.value = false
  }
})
</script>
