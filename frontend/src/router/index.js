import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/landing', component: () => import('../views/LandingView.vue') },
  { path: '/chat', component: () => import('../views/ChatView.vue') },
  { path: '/login', component: () => import('../views/LoginView.vue') },
  { path: '/register', component: () => import('../views/RegisterView.vue') },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('../views/DashboardView.vue') },
      { path: 'children', component: () => import('../views/ChildrenView.vue') },
      { path: 'children/:id', component: () => import('../views/ChildDetailView.vue') },
      { path: 'classes', component: () => import('../views/ClassesView.vue') },
      { path: 'scale-library', component: () => import('../views/ScaleLibraryView.vue') },
      { path: 'assessment', component: () => import('../views/AssessmentView.vue') },
      { path: 'diagnosis', component: () => import('../views/DiagnosisView.vue') },
      { path: 'training', component: () => import('../views/TrainingView.vue') },
      { path: 'report', component: () => import('../views/ReportView.vue') },
      { path: 'iep', component: () => import('../views/IepView.vue') },
      { path: 'teaching', component: () => import('../views/TeachingView.vue') },
      { path: 'teaching-archive', component: () => import('../views/TeachingArchiveView.vue') },
      { path: 'research', component: () => import('../views/ResearchView.vue') },
      { path: 'aids', component: () => import('../views/AidsView.vue') },
      { path: 'qa', component: () => import('../views/QaView.vue') },
      { path: 'family-iep', component: () => import('../views/FamilyIepView.vue') },
      { path: 'users', component: () => import('../views/UsersView.vue') }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  // 公开页(免登):落地页、公开问答、登录、注册
  const publicPaths = ['/landing', '/chat', '/login', '/register']
  if (!publicPaths.includes(to.path) && !auth.isLoggedIn) {
    return '/landing'
  }
})

export default router
