<template>
  <div class="app-shell">
    <header class="app-header">
      <div>
        <span class="brand-kicker">高级 WEB 期末项目</span>
        <h1 class="brand-title">机器学习指导仪表板</h1>
      </div>
      <nav class="header-nav">
        <router-link class="nav-pill" :class="{ active: $route.path === '/' }" to="/">训练中心</router-link>
        <router-link class="nav-pill" :class="{ active: $route.path === '/learning' }" to="/learning">学习中心</router-link>
      </nav>
    </header>
    <main class="app-main">
      <!-- 对应原项目的 keep-alive-route-reuse.strategy：学习中心切走再切回时保留状态 -->
      <router-view v-slot="{ Component }">
        <keep-alive include="LearningView">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </main>
  </div>
</template>

<script setup>
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #eef2f9;
}

.header-nav {
  display: flex;
  gap: 10px;
}

.nav-pill {
  padding: 8px 20px;
  font-size: 13px;
  color: #eef2ff;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 999px;
}

.nav-pill.active {
  color: #3d4a63;
  background: #fff;
  font-weight: 600;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 32px 22px;
  background: linear-gradient(120deg, #3d4a63 0%, #4a5a76 60%, #56688a 100%);
  color: #fff;
}

.brand-kicker {
  display: block;
  font-size: 12px;
  letter-spacing: 0.12em;
  opacity: 0.78;
}

.brand-title {
  margin: 4px 0 0;
  font-size: 26px;
  font-weight: 700;
}

.app-main {
  max-width: 1680px;
  margin: 0 auto;
}
</style>
