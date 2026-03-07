<template>
  <div class="page-container py-8">
    <!-- 로딩 -->
    <div v-if="loading" class="animate-pulse">
      <div class="flex gap-6">
        <div class="skeleton w-40 h-56 rounded-xl flex-shrink-0"></div>
        <div class="flex-1 space-y-3 pt-2">
          <div class="skeleton h-7 rounded w-3/4"></div>
          <div class="skeleton h-4 rounded w-1/2"></div>
          <div class="skeleton h-4 rounded w-1/3"></div>
        </div>
      </div>
    </div>

    <!-- 에러 -->
    <div v-else-if="error" class="text-center py-24">
      <p class="text-5xl mb-4">😕</p>
      <p class="text-gray-600 font-semibold">도서 정보를 불러올 수 없습니다.</p>
      <button @click="$router.back()" class="btn-outline mt-4">뒤로가기</button>
    </div>

    <!-- 상세 -->
    <div v-else-if="book">
      <!-- 상단: 표지 + 기본 정보 -->
      <div class="flex flex-col sm:flex-row gap-8 mb-8">
        <!-- 표지 -->
        <div class="flex-shrink-0 mx-auto sm:mx-0">
          <img
            :src="book.coverUrl || placeholder"
            :alt="book.title"
            class="w-40 sm:w-48 rounded-xl shadow-lg object-cover"
            @error="e => e.target.src = placeholder"
          />
        </div>

        <!-- 정보 -->
        <div class="flex-1">
          <h1 class="text-2xl sm:text-3xl font-black text-gray-900 leading-tight">{{ book.title }}</h1>
          <div class="mt-3 space-y-1 text-sm text-gray-600">
            <p><span class="font-medium text-gray-800">저자</span> · {{ book.author || '정보 없음' }}</p>
            <p><span class="font-medium text-gray-800">출판사</span> · {{ book.publisher || '정보 없음' }}</p>
            <p><span class="font-medium text-gray-800">ISBN-13</span> · <span class="font-mono text-xs bg-gray-100 px-2 py-0.5 rounded">{{ book.isbn13 }}</span></p>
          </div>

          <!-- 도서관 상태 배지 -->
          <div class="mt-4">
            <span v-if="book.library?.holding === true">
              <span v-if="book.library?.status === 'AVAILABLE'" class="badge-available text-sm px-3 py-1">
                ✓ 대출 가능
              </span>
              <span v-else class="badge-unavailable text-sm px-3 py-1">
                {{ book.library?.statusText || '대출 중' }}
              </span>
            </span>
            <span v-else class="badge-unknown text-sm px-3 py-1">
              미소장
            </span>
          </div>

          <!-- 구매 링크 -->
          <div v-if="book.vendors" class="mt-5 flex flex-wrap gap-2">
            <a :href="book.vendors.aladin" target="_blank" rel="noopener noreferrer"
               class="inline-flex items-center gap-1.5 px-4 py-2 bg-orange-500 hover:bg-orange-600
                      text-white text-sm font-semibold rounded-lg transition-colors">
              알라딘
            </a>
            <a :href="book.vendors.kyobo" target="_blank" rel="noopener noreferrer"
               class="inline-flex items-center gap-1.5 px-4 py-2 bg-red-600 hover:bg-red-700
                      text-white text-sm font-semibold rounded-lg transition-colors">
              교보문고
            </a>
            <a :href="book.vendors.yes24" target="_blank" rel="noopener noreferrer"
               class="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 hover:bg-blue-700
                      text-white text-sm font-semibold rounded-lg transition-colors">
              YES24
            </a>
          </div>
        </div>
      </div>

      <!-- 정보 카드들 -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <!-- 도서관 대출 정보 -->
        <div class="card p-5">
          <h2 class="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <span class="w-5 h-5 text-blue-600">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M8 14v3m4-3v3m4-3v3M3 21h18M3 10h18M3 7l9-4 9 4M4 10h16v11H4V10z"/>
              </svg>
            </span>
            도서관 대출 정보
          </h2>
          <template v-if="book.library">
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between">
                <dt class="text-gray-500">소장 여부</dt>
                <dd class="font-medium" :class="book.library.holding ? 'text-green-600' : 'text-gray-500'">
                  {{ book.library.holding ? '소장' : '미소장' }}
                </dd>
              </div>
              <div v-if="book.library.holding" class="flex justify-between">
                <dt class="text-gray-500">대출 상태</dt>
                <dd class="font-medium">{{ book.library.statusText || '-' }}</dd>
              </div>
              <div v-if="book.library.location" class="flex justify-between">
                <dt class="text-gray-500">위치</dt>
                <dd>{{ book.library.location }}</dd>
              </div>
              <div v-if="book.library.callNo" class="flex justify-between">
                <dt class="text-gray-500">청구기호</dt>
                <dd class="font-mono text-xs">{{ book.library.callNo }}</dd>
              </div>
            </dl>
            <a
              v-if="book.library.detailUrl"
              :href="book.library.detailUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="btn-outline mt-4 w-full text-sm py-2"
            >
              도서관 상세보기
            </a>
          </template>
          <p v-else class="text-gray-400 text-sm">정보 없음</p>
        </div>

        <!-- 전자책 정보 -->
        <div class="card p-5">
          <h2 class="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <span class="w-5 h-5 text-green-600">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
              </svg>
            </span>
            전자책 정보
          </h2>
          <template v-if="ebookLoading">
            <div class="flex flex-col items-center justify-center py-6">
              <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-green-600 mb-2"></div>
              <p class="text-sm text-gray-400">전자책 정보 로딩 중...</p>
            </div>
          </template>
          <template v-else-if="book.ebook?.found">
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between">
                <dt class="text-gray-500">전체 권수</dt>
                <dd>{{ book.ebook.totalHoldings }}권</dd>
              </div>
              <div class="flex justify-between">
                <dt class="text-gray-500">대출 가능</dt>
                <dd :class="book.ebook.availableHoldings > 0 ? 'text-green-600 font-medium' : 'text-red-500'">
                  {{ book.ebook.availableHoldings }}권
                </dd>
              </div>
              <div class="flex justify-between">
                <dt class="text-gray-500">상태</dt>
                <dd>{{ book.ebook.statusText }}</dd>
              </div>
            </dl>
            <a
              v-if="book.ebook.deepLinkUrl"
              :href="book.ebook.deepLinkUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="btn-primary mt-4 w-full text-sm py-2"
            >
              전자책 대출하기
            </a>
          </template>
          <div v-else class="flex flex-col items-center justify-center py-6 text-gray-400">
            <svg xmlns="http://www.w3.org/2000/svg" class="w-10 h-10 mb-2 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
            <p class="text-sm">전자책 정보 없음</p>
          </div>
        </div>

        <!-- 가격 비교 -->
        <div class="card p-5 md:col-span-2">
          <h2 class="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <span class="w-5 h-5 text-amber-600">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
              </svg>
            </span>
            가격 비교
          </h2>

          <div v-if="pricesLoading" class="flex gap-3">
            <div v-for="n in 3" :key="n" class="skeleton h-20 rounded-lg flex-1"></div>
          </div>
          <div v-else-if="filteredPrices.length > 0" class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <a
              v-for="price in filteredPrices"
              :key="price.store"
              :href="price.url"
              target="_blank"
              rel="noopener noreferrer"
              class="block border rounded-xl p-4 transition-colors group"
              :class="vendorStyle(price.store).border"
            >
              <p class="text-sm font-bold transition-colors"
                 :class="vendorStyle(price.store).text">
                {{ price.storeName || vendorLabel(price.store) }}
              </p>
              <p v-if="price.price" class="text-xl font-black text-gray-900 mt-1">
                {{ price.price.toLocaleString() }}<span class="text-sm font-normal ml-0.5">원</span>
              </p>
              <p v-else class="text-sm text-gray-400 mt-1">가격 정보 없음</p>
              <p v-if="price.discountRate" class="text-xs text-red-500 mt-0.5">{{ price.discountRate }}% 할인</p>
            </a>
          </div>
          <p v-else class="text-sm text-gray-400">가격 정보를 불러올 수 없습니다.</p>
        </div>
      </div>

      <button @click="$router.back()" class="btn-outline mt-6">← 뒤로가기</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'

const route    = useRoute()
const isbn13   = route.params.isbn13
const book     = ref(null)
const loading  = ref(true)
const error    = ref(false)
const prices   = ref([])
const pricesLoading = ref(false)
const ebookLoading = ref(false)
const placeholder = 'https://placehold.co/300x440?text=No+Cover'

// 알라딘, YES24, 교보문고만 고정 순서로 표시
const ALLOWED_STORES = ['aladin', 'yes24', 'kyobo']
const filteredPrices = computed(() => {
  const map = Object.fromEntries(prices.value.map(p => [p.store, p]))
  return ALLOWED_STORES.map(id => map[id]).filter(Boolean)
})

function vendorLabel(store) {
  const labels = { aladin: '알라딘', yes24: 'YES24', kyobo: '교보문고' }
  return labels[store] || store
}

function vendorStyle(store) {
  const styles = {
    aladin: {
      border: 'border-orange-200 hover:border-orange-400',
      text:   'text-orange-600 group-hover:text-orange-700'
    },
    yes24: {
      border: 'border-blue-200 hover:border-blue-400',
      text:   'text-blue-600 group-hover:text-blue-700'
    },
    kyobo: {
      border: 'border-red-200 hover:border-red-400',
      text:   'text-red-600 group-hover:text-red-700'
    }
  }
  return styles[store] || {
    border: 'border-gray-200 hover:border-yju',
    text:   'text-gray-700 group-hover:text-yju'
  }
}

async function fetchDetail() {
  loading.value = true
  error.value   = false
  try {
    const { data } = await api.getBookDetail(isbn13)
    book.value = data
    fetchPrices(isbn13, data.title)
    // 전자책 정보를 별도로 비동기 로드 (도서 상세 먼저 표시)
    fetchEbook(isbn13)
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function fetchEbook(isbn) {
  ebookLoading.value = true
  try {
    const { data } = await api.getEbookInfo(isbn)
    if (book.value) {
      book.value = { ...book.value, ebook: data }
    }
  } catch {
    // 전자책 로드 실패 시 기존 데이터 유지
  } finally {
    ebookLoading.value = false
  }
}

async function fetchPrices(isbn, title) {
  pricesLoading.value = true
  try {
    const { data } = await api.getBookPrices(isbn, title)
    prices.value = data || []
  } catch {
    prices.value = []
  } finally {
    pricesLoading.value = false
  }
}

onMounted(fetchDetail)
</script>
