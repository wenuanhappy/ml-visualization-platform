import { createRouter, createWebHistory } from 'vue-router'
import ExperimentView from '@/views/lab/ExperimentView.vue'
import LearningView from '@/views/lab/LearningView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'experiment', component: ExperimentView },
    { path: '/learning', name: 'learning', component: LearningView }
  ]
})

export default router
