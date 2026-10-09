<template>
  <main class="auth-page">
    <section class="auth-story">
      <RouterLink class="brand" to="/login"><span class="brand-mark">S</span>秒杀平台</RouterLink>
      <div>
        <p class="eyebrow">FLASH SALE PLATFORM</p>
        <h1>先登录，<br />再开启你的秒杀之旅。</h1>
        <p class="story-copy">一个账户，连接商品与订单。</p>
      </div>
      <p class="story-footer">Spring Boot × Vue 3</p>
    </section>
    <section class="auth-panel"><div class="auth-form"><slot /></div></section>
  </main>
</template>
