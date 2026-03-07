<template>
  <section v-if="banners.length > 0" class="relative bg-gray-100 py-5">
    <!-- 플로팅 배지 (세미-투명) -->
    <div class="absolute top-7 left-6 z-20 bg-black/40 text-white text-[10px] font-semibold
                px-2.5 py-1 rounded-full backdrop-blur-md shadow-sm tracking-wide">
      📢 이벤트 · 공지
    </div>

    <swiper
      :modules="modules"
      :autoplay="{ delay: 4500, disableOnInteraction: false, pauseOnMouseEnter: true }"
      :pagination="{ clickable: true }"
      :loop="banners.length > 1"
      :speed="500"
      :slides-per-view="1.15"
      :centered-slides="true"
      :space-between="12"
      :breakpoints="{
        640:  { slidesPerView: 1.25, spaceBetween: 16 },
        1024: { slidesPerView: 1.35, spaceBetween: 20 }
      }"
      :grab-cursor="true"
      class="notice-swiper"
    >
      <swiper-slide v-for="banner in banners" :key="banner.boardId">
        <a
          :href="banner.linkUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="block relative w-full aspect-[16/7] rounded-xl overflow-hidden
                 border border-gray-200/60 shadow-sm group"
        >
          <!-- 이벤트 포스터 이미지 -->
          <img
            :src="proxyImage(banner.imageUrl)"
            :alt="banner.title"
            class="w-full h-full object-cover group-hover:scale-[1.03]
                   transition-transform duration-500 ease-out"
            @error="onImgError"
            loading="lazy"
          />
          <!-- 하단 그라데이션 -->
          <div class="absolute inset-x-0 bottom-0 h-1/3
                      bg-gradient-to-t from-black/60 to-transparent pointer-events-none" />
          <!-- 텍스트 오버레이 -->
          <div class="absolute bottom-0 inset-x-0 px-4 pb-3 sm:px-5 sm:pb-4">
            <p class="text-white text-xs sm:text-sm font-bold line-clamp-1 drop-shadow-md">
              {{ banner.title }}
            </p>
            <p v-if="banner.postedDate"
               class="text-white/60 text-[10px] sm:text-xs mt-0.5 font-normal">
              {{ banner.postedDate }}
            </p>
          </div>
        </a>
      </swiper-slide>
    </swiper>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Swiper, SwiperSlide } from 'swiper/vue'
import { Autoplay, Pagination } from 'swiper/modules'
import 'swiper/css'
import 'swiper/css/pagination'
import api from '@/api'

const modules = [Autoplay, Pagination]
const banners = ref([])

function proxyImage(url) {
  if (!url) return 'https://placehold.co/800x350?text=공지사항'
  if (url.startsWith('http') && !url.includes(window.location.host)) {
    return `/library-api/proxy/image?url=${encodeURIComponent(url)}`
  }
  return url
}

function onImgError(e) {
  e.target.src = 'https://placehold.co/800x350?text=이미지+로딩+실패'
}

onMounted(async () => {
  try {
    const { data } = await api.getNoticeBanners()
    banners.value = data || []
  } catch {
    banners.value = []
  }
})
</script>

<style scoped>
.notice-swiper {
  width: 100%;
  padding-bottom: 28px;       /* 인디케이터 공간 */
}

/* 미니멀 인디케이터 도트 */
.notice-swiper :deep(.swiper-pagination-bullet) {
  width: 6px;
  height: 6px;
  background: #cbd5e1;        /* slate-300 */
  opacity: 1;
  transition: all 0.3s ease;
}
.notice-swiper :deep(.swiper-pagination-bullet-active) {
  width: 18px;
  border-radius: 9999px;
  background: #1e3a8a;        /* yju primary */
}

/* 비활성 슬라이드 살짝 흐림 (Peek-a-boo 효과) */
.notice-swiper :deep(.swiper-slide) {
  transition: opacity 0.4s ease, transform 0.4s ease;
  opacity: 0.55;
}
.notice-swiper :deep(.swiper-slide-active) {
  opacity: 1;
}
</style>

