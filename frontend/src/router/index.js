import { createRouter, createWebHistory } from 'vue-router'
import ExperimentView from '@/views/lab/ExperimentView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', name: 'experiment', component: ExperimentView }]
})

export default router
