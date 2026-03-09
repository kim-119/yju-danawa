<template>
  <div>
    <!-- 헤더 -->
    <section class="bg-gradient-to-br from-yju-dark via-yju to-yju-light text-white">
      <div class="page-container py-10 text-center">
        <h1 class="text-3xl font-black mb-2">중고 마켓</h1>
        <p class="text-blue-200 text-sm mb-4">영진전문대 학생 간 중고 교재 거래</p>

        <RouterLink
          v-if="auth.isLoggedIn"
          to="/market/write"
          class="inline-flex items-center gap-1.5 bg-yju-accent hover:bg-amber-400 text-white
                 font-bold text-sm px-5 py-2.5 rounded-xl transition-colors mb-6 shadow"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 4v16m8-8H4"/>
          </svg>
          책 등록하기
        </RouterLink>
        <div v-else class="mb-6">
          <RouterLink
            to="/login"
            class="inline-flex items-center gap-1.5 bg-white/20 hover:bg-white/30 text-white
                   font-semibold text-sm px-5 py-2.5 rounded-xl transition-colors"
          >
            로그인 후 등록하기
          </RouterLink>
        </div>

        <form @submit.prevent="doSearch" class="max-w-xl mx-auto">
          <div class="flex gap-2 bg-white rounded-xl p-1.5 shadow-xl">
            <input
              v-model="searchQuery"
              type="search"
              placeholder="책 제목 또는 ISBN 검색..."
              class="flex-1 px-4 py-2.5 text-gray-900 text-sm bg-transparent focus:outline-none placeholder-gray-400"
            />
            <button
              type="submit"
              class="bg-yju hover:bg-yju-light text-white font-bold px-6 py-2.5 rounded-lg transition-colors text-sm"
            >
              검색
            </button>
          </div>
        </form>
      </div>
    </section>

    <div class="page-container py-6">
      <!-- 검색 결과 모드 -->
      <div v-if="isSearchMode" class="mb-4 flex items-center justify-between">
        <p class="text-sm text-gray-600">
          <span class="font-semibold text-yju">"{{ currentQuery }}"</span> 검색 결과
          <span class="text-gray-400 ml-1">({{ totalCount }}건)</span>
        </p>
        <button @click="clearSearch" class="text-xs text-gray-400 hover:text-gray-600 underline">
          전체 목록 보기
        </button>
      </div>

      <!-- 목록 헤더 (검색 모드가 아닐 때) -->
      <div v-if="!isSearchMode" class="flex items-center justify-between mb-4">
        <p class="text-sm text-gray-500">
          총 <span class="font-semibold text-gray-800">{{ totalCount }}</span>개
        </p>
        <RouterLink
          v-if="auth.isLoggedIn"
          to="/market/write"
          class="flex items-center gap-1 text-sm font-semibold text-yju hover:text-yju-light transition-colors"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 4v16m8-8H4"/>
          </svg>
          글 등록
        </RouterLink>
      </div>

      <!-- 학과 탭 (검색 모드가 아닐 때) -->
      <div
        v-if="!isSearchMode"
        ref="tabsRef"
        class="flex gap-2 overflow-x-auto pb-2 scrollbar-hide select-none mb-5"
        :class="isDragging ? 'cursor-grabbing' : 'cursor-grab'"
        @mousedown="startDrag"
        @mousemove="onDrag"
        @mouseup="endDrag"
        @mouseleave="endDrag"
      >
        <button
          @click="selectDept(null)"
          :class="[
            'flex-shrink-0 px-4 py-1.5 rounded-full text-sm font-medium transition-all whitespace-nowrap',
            selectedDeptId === null ? 'bg-yju text-white shadow-sm' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          ]"
        >
          전체
        </button>
        <button
          v-for="dept in departments"
          :key="dept.id"
          @click="selectDept(dept.id)"
          :class="[
            'flex-shrink-0 px-4 py-1.5 rounded-full text-sm font-medium transition-all whitespace-nowrap',
            selectedDeptId === dept.id ? 'bg-yju text-white shadow-sm' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          ]"
        >
          {{ dept.name }}
        </button>
      </div>

      <!-- 스켈레톤 -->
      <div v-if="loading" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
        <div v-for="n in 8" :key="n" class="bg-white rounded-lg border border-gray-200/60 shadow-sm p-3">
          <div class="skeleton w-full aspect-[3/4] rounded mb-2"></div>
          <div class="skeleton h-3 rounded mb-1.5"></div>
          <div class="skeleton h-3 rounded w-2/3 mb-1.5"></div>
          <div class="skeleton h-4 rounded w-1/2"></div>
        </div>
      </div>

      <!-- 도서 목록 -->
      <div v-else-if="books.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
        <RouterLink
          v-for="book in books"
          :key="book.id"
          :to="{ name: 'market-detail', params: { id: book.id } }"
          class="bg-white rounded-lg border border-gray-200/60 shadow-sm p-3
                 hover:shadow-md hover:border-blue-200 transition-all group"
        >
          <div class="w-full aspect-[3/4] bg-gray-50 rounded overflow-hidden mb-2">
            <img
              :src="book.imageUrl || 'https://placehold.co/120x160?text=No+Cover'"
              :alt="book.title"
              class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
              @error="e => e.target.src = 'https://placehold.co/120x160?text=No+Cover'"
              loading="lazy"
            />
          </div>
          <p class="text-xs font-bold text-gray-800 line-clamp-2 leading-tight mb-1">{{ book.title }}</p>
          <p class="text-[11px] text-gray-400 truncate mb-1.5">{{ book.author }}</p>
          <div class="flex items-center justify-between gap-1">
            <p class="text-sm font-bold text-yju truncate">
              {{ book.priceWon ? book.priceWon.toLocaleString() + '원' : '가격 미정' }}
            </p>
            <span :class="statusBadge(book.status)" class="flex-shrink-0 text-[10px] font-medium px-1.5 py-0.5 rounded-full">
              {{ statusLabel(book.status) }}
            </span>
          </div>
        </RouterLink>
      </div>

      <!-- 빈 상태 -->
      <div v-else class="text-center py-20 text-gray-400">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-12 h-12 mx-auto mb-3 text-gray-200" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
            d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
        </svg>
        <p class="text-sm">{{ isSearchMode ? '검색 결과가 없습니다.' : '등록된 중고 서적이 없습니다.' }}</p>
      </div>

      <!-- 페이지네이션 -->
      <div v-if="totalPages > 1" class="flex justify-center gap-2 mt-8">
        <button
          @click="changePage(currentPage - 1)"
          :disabled="currentPage === 0"
          class="px-3 py-1.5 rounded-lg text-sm bg-gray-100 text-gray-600 hover:bg-gray-200
                 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          이전
        </button>
        <span class="px-3 py-1.5 text-sm text-gray-600">{{ currentPage + 1 }} / {{ totalPages }}</span>
        <button
          @click="changePage(currentPage + 1)"
          :disabled="currentPage >= totalPages - 1"
          class="px-3 py-1.5 rounded-lg text-sm bg-gray-100 text-gray-600 hover:bg-gray-200
                 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          다음
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api'
import { useAuthStore } from '@/stores/auth'

const route  = useRoute()
const router = useRouter()
const auth   = useAuthStore()

const departments   = ref([])
const selectedDeptId = ref(null)
const books         = ref([])
const loading       = ref(false)
const currentPage   = ref(0)
const totalPages    = ref(0)
const totalCount    = ref(0)
const searchQuery   = ref('')
const currentQuery  = ref('')
const isSearchMode  = ref(false)

// 드래그 스크롤
const tabsRef  = ref(null)
const isDragging = ref(false)
let startX = 0, scrollLeft = 0
function startDrag(e) {
  isDragging.value = true
  startX = e.pageX - tabsRef.value.offsetLeft
  scrollLeft = tabsRef.value.scrollLeft
}
function onDrag(e) {
  if (!isDragging.value) return
  e.preventDefault()
  tabsRef.value.scrollLeft = scrollLeft - (e.pageX - tabsRef.value.offsetLeft - startX)
}
function endDrag() { isDragging.value = false }

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

async function fetchBooks(page = 0) {
  loading.value = true
  try {
    const { data } = await api.getUsedBooks(selectedDeptId.value, page, 20)
    books.value    = data.items || []
    totalCount.value = data.total || 0
    totalPages.value = Math.ceil(totalCount.value / 20)
    currentPage.value = page
  } catch {
    books.value = []
  } finally {
    loading.value = false
  }
}

async function fetchSearch(q, page = 0) {
  loading.value = true
  try {
    const { data } = await api.searchUsedBooks(q, page, 20)
    books.value    = data.items || []
    totalCount.value = data.total || 0
    totalPages.value = Math.ceil(totalCount.value / 20)
    currentPage.value = page
  } catch {
    books.value = []
  } finally {
    loading.value = false
  }
}

function selectDept(deptId) {
  selectedDeptId.value = deptId
  fetchBooks(0)
}

function changePage(page) {
  if (page < 0 || page >= totalPages.value) return
  isSearchMode.value ? fetchSearch(currentQuery.value, page) : fetchBooks(page)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function doSearch() {
  const q = searchQuery.value.trim()
  if (!q) return
  isSearchMode.value = true
  currentQuery.value = q
  router.replace({ query: { q } })
  fetchSearch(q, 0)
}

function clearSearch() {
  isSearchMode.value = false
  searchQuery.value  = ''
  currentQuery.value = ''
  router.replace({ query: {} })
  fetchBooks(0)
}

onMounted(async () => {
  try {
    const { data } = await api.getDepartments()
    departments.value = data
  } catch {
    departments.value = []
  }

  const q = route.query.q
  if (q) {
    searchQuery.value  = q
    isSearchMode.value = true
    currentQuery.value = q
    await fetchSearch(q, 0)
  } else {
    await fetchBooks(0)
  }
})
</script>

<style scoped>
.scrollbar-hide::-webkit-scrollbar { display: none; }
.scrollbar-hide { -ms-overflow-style: none; scrollbar-width: none; }
</style>
