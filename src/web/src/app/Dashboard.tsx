import React from 'react';
import { LayoutDashboard, AlertTriangle, Machine, TrendingUp, Users, Settings, Activity, CheckCircle2, Clock } from 'lucide-react';

const Dashboard = () => {
  const [machines, setMachines] = React.useState([]);
  const [isLoading, setIsLoading] = React.useState(true);

  React.useEffect(() => {
    setTimeout(() => {
      setMachines([
        { id: 'M-01', name: 'Vấn 01', status: 'RUNNING', currentOutput: 450, targetOutput: 500 },
        { id: 'M-02', name: 'Bao 02', status: 'STOPPED', currentOutput: 300, targetOutput: 500, lastStopReason: 'E (Giờ ăn)' },
        { id: 'M-03', name: 'Vấn 03', status: 'RUNNING', currentOutput: 480, targetOutput: 500 },
        { id: 'M-04', name: 'Bao 04', status: 'MAINTENANCE', currentOutput: 0, targetOutput: 0 },
        { id: 'M-05', name: 'Vấn 05', status: 'RUNNING', currentOutput: 420, targetOutput: 500 },
      ]);
      setIsLoading(false);
    }, 800);
  }, []);

  if (isLoading) return <div className="p-8 flex justify-center items-center h-screen font-sans">Loading Dashboard...</div>;

  return (
    <div className="min-h-screen bg-gray-50 flex font-sans text-gray-900">
      {/* Sidebar */}
      <aside className="w-64 bg-slate-900 text-white p-6 flex flex-col">
        <h1 className="text-2xl font-bold mb-10 text-blue-400">PXVB Admin</h1>
        <nav className="space-y-4 flex-1">
          <button className="flex items-center space-x-3 p-3 bg-blue-600 rounded-lg w-full text-left"><LayoutDashboard size={20}/> <span>Dashboard</span></button>
          <button className="flex items-center space-x-3 p-3 hover:bg-slate-800 rounded-lg w-full text-left"><Machine size={20}/> <span>Máy móc</span></button>
          <button className="flex items-center space-x-3 p-3 hover:bg-slate-800 rounded-lg w-full text-left"><Users size={20}/> <span>Nhân sự</span></button>
          <button className="flex items-center space-x-3 p-3 hover:bg-slate-800 rounded-lg w-full text-left"><Settings size={20}/> <span>Cấu hình</span></button>
        </nav>
      </aside>

      {/* Main Content */}
      <main className="flex-1 p-8 overflow-auto">
        <header className="flex justify-between items-center mb-8">
          <div>
            <h2 className="text-3xl font-bold text-gray-800">Trực vận hành ca A</h2>
            <p className="text-gray-500">Cập nhật realtime sản lượng & OEE</p>
          </div>
          <div className="flex items-center space-x-4">
            <span className="bg-green-100 text-green-800 px-3 py-1 rounded-full text-sm font-medium">Hệ thống Online</span>
            <div className="w-10 h-10 bg-blue-500 rounded-full flex items-center justify-center text-white">S</div>
          </div>
        </header>

        {/* KPI Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
            <div className="flex justify-between items-center mb-4">
              <span className="text-gray-500 text-sm">Tổng sản lượng</span>
              <TrendingUp className="text-green-500" size={20}/>
            </div>
            <div className="text-2xl font-bold">2.150 bao</div>
            <div className="text-xs text-green-600">+2.4% so với ca trước</div>
          </div>
          <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
            <div className="flex justify-between items-center mb-4">
              <span className="text-gray-500 text-sm">OEE trung bình</span>
              <Activity className="text-blue-500" size={20}/>
            </div>
            <div className="text-2xl font-bold">84.2%</div>
            <div className="text-xs text-red-600">-1.2% so với mục tiêu</div>
          </div>
          <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
            <div className="flex justify-between items-center mb-4">
              <span className="text-gray-500 text-sm">Lỗi A/B/C</span>
              <AlertTriangle className="text-yellow-500" size={20}/>
            </div>
            <div className="text-2xl font-bold">12</div>
            <div className="text-xs text-gray-500">Cần xử lý ngay</div>
          </div>
          <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
            <div className="flex justify-between items-center mb-4">
              <span className="text-gray-500 text-sm">Máy đang dừng</span>
              <Clock className="text-red-500" size={20}/>
            </div>
            <div className="text-2xl font-bold">03</div>
            <div className="text-xs text-gray-500">Xem chi tiết dừng máy</div>
          </div>
        </div>

        {/* Machine Grid */}
        <h3 className="text-xl font-bold mb-4 text-gray-800">Trạng thái máy (Realtime)</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-4">
          {machines.map(m => (
            <div key={m.id} className={`p-4 rounded-xl border shadow-sm ${
              m.status === 'RUNNING' ? 'bg-green-50 border-green-200' : 
              m.status === 'STOPPED' ? 'bg-red-50 border-red-200' : 'bg-gray-50 border-gray-200'
            }`}>
              <div className="flex justify-between items-start mb-2">
                <span className="font-bold">{m.name}</span>
                {m.status === 'RUNNING' && <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"/>}
              </div>
              <div className="text-sm text-gray-600 mb-4">
                Trạng thái: <span className="font-medium">{m.status}</span>
              </div>
              <div className="space-y-1">
                <div className="flex justify-between text-xs">
                  <span>Thực tế:</span>
                  <span className="font-bold">{m.currentOutput}</span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-1.5">
                  <div 
                    className={`h-1.5 rounded-full ${m.status === 'RUNNING' ? 'bg-green-500' : 'bg-red-500'}`}
                    style={{ width: `${(m.currentOutput / m.targetOutput) * 100}%` }}
                  />
                </div>
                <div className="flex justify-between text-xs mt-1">
                  <span>Mục tiêu:</span>
                  <span>{m.targetOutput}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </main>
    </div>
  );
};

export default Dashboard;
