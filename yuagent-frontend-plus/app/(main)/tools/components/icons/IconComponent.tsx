import { useEffect, useState } from 'react';
import { Wrench } from 'lucide-react';

interface IconComponentProps {
  icon: string | null;
  className?: string;
}

/**
 * 图标组件，根据传入的图标URL显示图像，如果没有图标则显示默认图标
 */
const IconComponent: React.FC<IconComponentProps> = ({ icon, className = '' }) => {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [icon]);

  if (!icon || failed) {
    return <Wrench className={className} />;
  }

  return (
    <img 
      src={icon} 
      alt="工具图标" 
      className={`object-contain ${className}`} 
      onError={() => setFailed(true)}
    />
  );
};

export default IconComponent; 
