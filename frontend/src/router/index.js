import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/login', component: () => import('../views/LoginView.vue') },
  { path: '/register', component: () => import('../views/RegisterView.vue') },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/children' },
      { path: 'children', component: () => import('../views/ChildrenView.vue') },
      { path: 'children/:id', component: () => import('../views/ChildDetailView.vue') },
      { path: 'classes', component: () => import('../views/ClassesView.vue') },
      { path: 'scale-library', component: () => import('../views/ScaleLibraryView.vue') },
      { path: 'assessment', component: () => import('../views/AssessmentView.vue') },
      { path: 'report', component: () => import('../views/ReportView.vue') },
      { path: 'iep', component: () => import('../views/IepView.vue') },
      { path: 'users', component: () => import('../views/UsersView.vue') }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && to.path !== '/register' && !auth.isLoggedIn) {
    return '/login'
  }
})

export default router
