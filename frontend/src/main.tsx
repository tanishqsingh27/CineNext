import React, { lazy, Suspense } from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { EmptyState } from './components/States';
import './styles.css';

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 } } });
const Discover = lazy(() => import('./pages/Discover').then((module) => ({default: module.Discover})));
const MovieDetail = lazy(() => import('./pages/MovieDetail').then((module) => ({default: module.MovieDetail})));
const Cinemas = lazy(() => import('./pages/Cinemas').then((module) => ({default: module.Cinemas})));
const Watchlist = lazy(() => import('./pages/Watchlist').then((module) => ({default: module.Watchlist})));
const Login = lazy(() => import('./pages/Login').then((module) => ({default: module.Login})));
const Checkout = lazy(() => import('./pages/Checkout').then((module) => ({default: module.Checkout})));
const Bookings = lazy(() => import('./pages/Bookings').then((module) => ({default: module.Bookings})));
const Admin = lazy(() => import('./pages/Admin').then((module) => ({default: module.Admin})));
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={queryClient}><BrowserRouter><Suspense fallback={<div className="page-shell detail-loading" role="status">Setting the scene…</div>}><Routes><Route element={<Layout/>}><Route index element={<Discover/>}/><Route path="movies/:id" element={<MovieDetail/>}/><Route path="cinemas" element={<Cinemas/>}/><Route path="watchlist" element={<Watchlist/>}/><Route path="login" element={<Login/>}/><Route path="checkout/:id" element={<Checkout/>}/><Route path="bookings" element={<Bookings/>}/><Route path="bookings/:id" element={<Bookings/>}/><Route path="admin" element={<Admin/>}/><Route path="*" element={<div className="page-shell"><EmptyState title="This scene is missing" message="The page you’re looking for has left the screen."/></div>}/></Route></Routes></Suspense></BrowserRouter></QueryClientProvider></React.StrictMode>);
