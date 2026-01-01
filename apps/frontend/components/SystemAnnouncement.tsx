import React, { useEffect, useState } from 'react';
import { doc, onSnapshot } from 'firebase/firestore';
import { db } from '../firebase';
import { SystemAnnouncement as AnnouncementType } from '../types';

const SystemAnnouncement = () => {
  const [announcement, setAnnouncement] = useState<AnnouncementType | null>(null);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    if (!db) return;

    const unsub = onSnapshot(doc(db, 'app_settings', 'announcement'), (docSnap) => {
      if (docSnap.exists()) {
        const data = docSnap.data() as AnnouncementType;
        setAnnouncement(data);
        checkVisibility(data);
      } else {
        setIsVisible(false);
      }
    });

    return () => unsub();
  }, []);

  const checkVisibility = (data: AnnouncementType) => {
    if (!data.isEnabled) {
      setIsVisible(false);
      return;
    }

    const now = new Date();
    const start = data.startAt?.toDate ? data.startAt.toDate() : new Date(data.startAt);
    const end = data.endAt?.toDate ? data.endAt.toDate() : new Date(data.endAt);

    if (now >= start && now <= end) {
      setIsVisible(true);
    } else {
      setIsVisible(false);
    }
  };

  if (!isVisible || !announcement) return null;

  const getColors = () => {
    switch (announcement.type) {
      case 'warning': return 'bg-orange-500 text-white';
      case 'error': return 'bg-rose-500 text-white';
      default: return 'bg-indigo-600 text-white'; // info
    }
  };

  return (
    // 外層容器
    <div className={`${getColors()} overflow-hidden py-2 shadow-md relative z-50`}>
      <div className="flex items-center w-full">
        
        {/* ❌ 這裡原本的「公告」區塊已移除 */}

        {/* 跑馬燈捲動區域 (現在佔滿寬度) */}
        <div className="flex-1 overflow-hidden relative h-6 w-full">
           <div className="whitespace-nowrap absolute animate-marquee top-0 left-full">
              <span className="text-sm font-medium px-4 inline-block">
                {announcement.text}
              </span>
           </div>
        </div>
      </div>
    </div>
  );
};

export default SystemAnnouncement;