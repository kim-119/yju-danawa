<template>
  <div class="page-container py-8">
    <!-- 검색 헤더 -->
    <div class="mb-6">
      <h1 class="text-xl font-bold text-gray-800">
        "<span class="text-yju">{{ q }}</span>" 검색 결과
      </h1>
      <p v-if="!loading" class="text-sm text-gray-500 mt-1">
        총 <strong class="text-gray-700">{{ books.length }}</strong>건
      </p>
    </div>

    <!-- 로딩 스켈레톤 -->
    <div v-if="loading" class="space-y-3">
      <div v-for="n in 8" :key="n" class="card flex gap-3 p-3">
        <div class="skeleton w-16 h-24 rounded flex-shrink-0"></div>
        <div class="flex-1 py-1 space-y-2">
          <div class="skeleton h-4 rounded w-3/4"></div>
          <div class="skeleton h-3 rounded w-1/2"></div>
          <div class="skeleton h-3 rounded w-1/3"></div>
        </div>
      </div>
    </div>

    <!-- 에러 -->
    <div v-else-if="error" class="text-center py-20">
      <div class="text-5xl mb-4">⚠️</div>
      <p class="text-gray-600">검색 중 오류가 발생했습니다.</p>
      <button @click="fetchBooks" class="btn-primary mt-4">다시 시도</button>
    </div>

    <!-- 결과 없음 -->
    <div v-else-if="books.length === 0" class="text-center py-20">
      <div class="text-6xl mb-4">📚</div>
      <p class="text-gray-700 font-semibold text-lg">검색 결과가 없습니다</p>
      <p class="text-gray-400 mt-2 text-sm">"{{ q }}"에 대한 도서를 찾지 못했습니다.</p>
      <RouterLink to="/" class="btn-primary mt-6 inline-flex">홈으로</RouterLink>
    </div>

    <!-- Fallback 메시지 -->
    <div v-else>
      <div v-if="fallbackMessage" class="mb-4 p-4 bg-amber-50 border border-amber-200 rounded-xl">
        <p class="text-amber-800 text-sm font-medium flex items-center gap-2">
          <span class="text-lg">💡</span>
          {{ fallbackMessage }}
        </p>
      </div>

      <!-- 결과 목록 -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        <BookCard v-for="book in books" :key="book.isbn13 || book.title" :book="book" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import BookCard from '@/components/BookCard.vue'

const route  = useRoute()
const books  = ref([])
const loading = ref(false)
const error   = ref(false)
const fallbackMessage = ref('')
const q = ref(route.query.q || '')

async function fetchBooks() {
  if (!q.value.trim()) return
  loading.value = true
  error.value   = false
  fallbackMessage.value = ''
  try {
    const { data } = await api.searchBooks(q.value)
    // API가 { items, fallback, fallbackMessage } 또는 배열을 반환
    if (Array.isArray(data)) {
      books.value = data
    } else {
      books.value = data.items || []
      if (data.fallback && data.fallbackMessage) {
        fallbackMessage.value = data.fallbackMessage
      }
    }
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(() => route.query.q, (val) => {
  q.value = val || ''
  fetchBooks()
})

onMounted(fetchBooks)
</script>
