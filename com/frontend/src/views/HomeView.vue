<template>
  <div>
    <!-- 히어로 섹션 -->
    <section class="bg-gradient-to-br from-yju-dark via-yju to-yju-light text-white">
      <div class="page-container py-20 text-center">
        <h1 class="text-4xl sm:text-5xl font-black mb-3 tracking-tight">
          <span class="text-yju-accent">Y</span>-다나와
        </h1>
        <p class="text-blue-200 text-lg mb-2">영진전문대 도서관 통합 도서 검색</p>
        <p class="text-blue-300 text-sm mb-10">
          소장 여부 · 가격 비교 · 전자책 대출을 한 번에
        </p>

        <form @submit.prevent="doSearch" class="max-w-2xl mx-auto">
          <div class="flex gap-2 bg-white rounded-xl p-1.5 shadow-xl">
            <input
              v-model="keyword"
              type="search"
              placeholder="도서명, 저자명, ISBN을 입력하세요..."
              class="flex-1 px-4 py-3 text-gray-900 text-base bg-transparent focus:outline-none placeholder-gray-400"
              autofocus
            />
            <button
              type="submit"
              class="bg-yju hover:bg-yju-light text-white font-bold px-8 py-3 rounded-lg transition-colors flex items-center gap-2"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
              </svg>
              검색
            </button>
          </div>
        </form>
      </div>
    </section>

    <!-- 공지사항 배너 슬라이더 -->
    <NoticeBanner />

    <!-- 소개 카드 -->
    <section class="page-container py-10">
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div v-for="feat in features" :key="feat.title"
             class="bg-white rounded-xl border border-gray-200/80 shadow-sm
                    p-4 text-center hover:shadow-md transition-shadow">
          <div class="w-10 h-10 rounded-lg flex items-center justify-center mx-auto mb-3"
               :class="feat.bg">
            <component :is="feat.icon" class="w-5 h-5" :class="feat.color" />
          </div>
          <h3 class="font-bold text-sm text-gray-900 mb-1">{{ feat.title }}</h3>
          <p class="text-xs text-gray-400 leading-relaxed">{{ feat.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 실시간 인기 검색어 -->
    <section class="page-container py-6">
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="md:col-span-1">
          <PopularSearch />
        </div>
        <div class="md:col-span-2 flex flex-col gap-3">
          <div class="bg-white rounded-xl border border-gray-200/80 shadow-sm p-4">
            <p class="text-xs text-gray-400 font-medium mb-1">이용 안내</p>
            <p class="text-sm text-gray-600">도서명 또는 저자명으로 검색하면 인기 검색어 순위에 반영됩니다.</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 소장 도서 -->
    <section class="bg-white border-t border-gray-100">
      <div class="page-container py-8">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-bold text-gray-900">소장 도서</h2>
        </div>

        <div v-if="loading" class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-3">
          <div v-for="n in 12" :key="n"
               class="bg-white rounded-lg border border-gray-200/60 shadow-sm p-2">
            <div class="skeleton w-full aspect-[3/4] rounded mb-1.5"></div>
            <div class="skeleton h-2.5 rounded mb-1"></div>
            <div class="skeleton h-2.5 rounded w-2/3"></div>
          </div>
        </div>

        <div v-else class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-3">
          <RouterLink
            v-for="book in recentBooks"
            :key="book.isbn || book.title"
            :to="book.isbn ? { name: 'book-detail', params: { isbn13: book.isbn } } : '#'"
            class="bg-white rounded-lg border border-gray-200/60 shadow-sm
                   p-2 hover:shadow-md hover:border-blue-200 transition-all group"
          >
            <div class="w-full aspect-[3/4] bg-gray-50 rounded overflow-hidden mb-1.5">
              <img
                :src="book.imageUrl || 'https://placehold.co/120x160?text=No+Cover'"
                :alt="book.title"
                class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                @error="e => e.target.src = 'https://placehold.co/120x160?text=No+Cover'"
                loading="lazy"
              />
            </div>
            <p class="text-[11px] font-bold text-gray-800 line-clamp-2 leading-tight">{{ book.title }}</p>
            <p class="text-[10px] text-gray-400 truncate mt-0.5 font-normal">{{ book.author }}</p>
          </RouterLink>
        </div>

        <div v-if="!loading && recentBooks.length === 0" class="text-center py-12 text-gray-400">
          <p class="text-sm">도서 정보를 불러오지 못했습니다.</p>
        </div>
      </div>
    </section>

    <!-- 학과별 중고 서적 -->
    <DeptCategoryTabs />

    <!-- 독서 일지 -->
    <section class="bg-gray-50 border-t border-gray-100">
      <div class="page-container py-8">
        <!-- 섹션 헤더 -->
        <div class="flex items-center justify-between mb-5">
          <div class="flex items-center gap-2">
            <span class="w-7 h-7 rounded-lg bg-blue-50 flex items-center justify-center">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
              </svg>
            </span>
            <h2 class="text-lg font-bold text-gray-900">독서 일지</h2>
          </div>
          <p v-if="auth.isLoggedIn" class="text-xs text-gray-400">날짜를 클릭해 독서 기록을 남겨보세요</p>
        </div>

        <!-- 로그인 상태: 캘린더 -->
        <ReadingCalendar v-if="auth.isLoggedIn" />

        <!-- 비로그인 상태: 안내 카드 -->
        <div v-else class="bg-white rounded-xl border border-gray-100 shadow-sm py-14 text-center">
          <div class="w-14 h-14 bg-blue-50 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg xmlns="http://www.w3.org/2000/svg" class="w-7 h-7 text-blue-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
            </svg>
          </div>
          <p class="text-sm font-semibold text-gray-600 mb-1">로그인 후 독서 일지를 이용할 수 있습니다.</p>
          <p class="text-xs text-gray-400 mb-5">달력에 읽은 책·페이지 수·메모를 기록해 보세요</p>
          <RouterLink to="/login" class="btn-primary text-sm py-2 px-5">로그인하기</RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import NoticeBanner from '@/components/NoticeBanner.vue'
import ReadingCalendar from '@/components/ReadingCalendar.vue'
import PopularSearch from '@/components/PopularSearch.vue'
import DeptCategoryTabs from '@/components/DeptCategoryTabs.vue'
import { useAuthStore } from '@/stores/auth'

const router  = useRouter()
const auth    = useAuthStore()
const keyword = ref('')
const loading = ref(true)
const recentBooks = ref([])

function doSearch() {
  if (!keyword.value.trim()) return
  router.push({ name: 'search', query: { q: keyword.value.trim() } })
}


// SVG 아이콘 컴포넌트
const LibraryIcon = {
  render: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', fill: 'none', viewBox: '0 0 24 24', stroke: 'currentColor' }, [
    h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'stroke-width': '2', d: 'M8 14v3m4-3v3m4-3v3M3 21h18M3 10h18M3 7l9-4 9 4M4 10h16v11H4V10z' })
  ])
}

const PriceIcon = {
  render: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', fill: 'none', viewBox: '0 0 24 24', stroke: 'currentColor' }, [
    h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'stroke-width': '2', d: 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z' })
  ])
}

const EbookIcon = {
  render: () => h('svg', { xmlns: 'http://www.w3.org/2000/svg', fill: 'none', viewBox: '0 0 24 24', stroke: 'currentColor' }, [
    h('path', { 'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'stroke-width': '2', d: 'M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253' })
  ])
}

const features = [
  {
    title: '도서관 소장 확인',
    desc: '영진전문대 도서관 실시간 대출 가능 여부와 위치를 즉시 확인',
    icon: LibraryIcon, bg: 'bg-blue-50', color: 'text-blue-600'
  },
  {
    title: '가격 비교',
    desc: '알라딘, 교보문고, YES24 최저가를 한눈에 비교',
    icon: PriceIcon, bg: 'bg-amber-50', color: 'text-amber-600'
  },
  {
    title: '전자책 대출',
    desc: '영진전문대 전자도서관 전자책 소장 여부 및 대출 현황 확인',
    icon: EbookIcon, bg: 'bg-green-50', color: 'text-green-600'
  }
]

onMounted(async () => {
  try {
    const { data } = await api.getBooksInfinite(undefined, 18)
    recentBooks.value = data.items || []
  } catch {
    recentBooks.value = []
  } finally {
    loading.value = false
  }
})
</script>
