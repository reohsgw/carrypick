import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import SparkleTrail from './components/SparkleTrail';
import { Loading } from './components/ui';
import { useSession } from './session';
import BuyerHome from './pages/BuyerHome';
import Checkout from './pages/Checkout';
import CountryOffers from './pages/CountryOffers';
import Login from './pages/Login';
import MyOrders from './pages/MyOrders';
import MyPage from './pages/MyPage';
import NewRequest from './pages/NewRequest';
import NewTrip from './pages/NewTrip';
import Notifications from './pages/Notifications';
import OfferDetail from './pages/OfferDetail';
import OfferForm from './pages/OfferForm';
import OrderDetail from './pages/OrderDetail';
import PayResult from './pages/PayResult';
import RequestBoard from './pages/RequestBoard';
import RequestDetail from './pages/RequestDetail';
import Search from './pages/Search';
import TravelerHome from './pages/TravelerHome';

/*
 * Screen map (IDs match the planning doc):
 *   C1 /login            C2 /me                 C3 /notifications
 *   B1 /                 B2 /country/:code      B3 /search          B4 /offers/:id
 *   B5 /requests/new     B6 /orders/:id/pay     B7 /orders          B8 /orders/:id (buyer view)
 *   T1 /t                T2 /t/trips/new        T3 /t/offers/new    T5 /t/requests
 *   T6 /t/requests/:id   T7 /orders/:id (traveler view)
 */
export default function App() {
  return (
    <div className="app">
      <Routes>
        <Route path="/login" element={<Login />} />

        <Route path="/" element={<RequireAuth><BuyerHome /></RequireAuth>} />
        <Route path="/country/:code" element={<RequireAuth><CountryOffers /></RequireAuth>} />
        <Route path="/search" element={<RequireAuth><Search /></RequireAuth>} />
        <Route path="/offers/:id" element={<RequireAuth><OfferDetail /></RequireAuth>} />
        <Route path="/requests/new" element={<RequireAuth><NewRequest /></RequireAuth>} />
        <Route path="/my-requests/:id" element={<RequireAuth><RequestDetail /></RequireAuth>} />
        <Route path="/orders" element={<RequireAuth><MyOrders /></RequireAuth>} />
        <Route path="/orders/:id" element={<RequireAuth><OrderDetail /></RequireAuth>} />
        <Route path="/orders/:id/pay" element={<RequireAuth><Checkout /></RequireAuth>} />
        <Route path="/pay/success" element={<RequireAuth><PayResult success /></RequireAuth>} />
        <Route path="/pay/fail" element={<RequireAuth><PayResult success={false} /></RequireAuth>} />

        <Route path="/t" element={<RequireAuth><TravelerHome /></RequireAuth>} />
        <Route path="/t/trips/new" element={<RequireAuth><NewTrip /></RequireAuth>} />
        <Route path="/t/offers/new" element={<RequireAuth><OfferForm /></RequireAuth>} />
        <Route path="/t/offers/:id/edit" element={<RequireAuth><OfferForm /></RequireAuth>} />
        <Route path="/t/requests" element={<RequireAuth><RequestBoard /></RequireAuth>} />
        <Route path="/t/requests/:id" element={<RequireAuth><RequestDetail /></RequireAuth>} />
        <Route path="/t/orders" element={<Navigate to="/orders?view=traveler" replace />} />

        <Route path="/me" element={<RequireAuth><MyPage /></RequireAuth>} />
        <Route path="/notifications" element={<RequireAuth><Notifications /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <SparkleTrail />
    </div>
  );
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useSession();
  const location = useLocation();
  if (loading) return <Loading />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}
