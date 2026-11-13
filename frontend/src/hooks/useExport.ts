import { useState, useEffect, useCallback } from 'react';
import { createSessionSocket, SessionSocket } from '../utils/sessionSocket';
import api from 'src/utils/api';
import useAuth from './useAuth';

interface UseExportReturn {
  exportEntity: (entity: ExportEntityType) => Promise<void>;
  loadingExport: Record<ExportEntityType, boolean>;
}

export type ExportEntityType =
  | 'work-orders'
  | 'assets'
  | 'locations'
  | 'parts'
  | 'meters'
  | 'preventive-maintenances'
  | 'costs-times'
  | 'part-transactions';

/**
 * Custom hook for exporting entities with WebSocket support
 * Generates a UUID, subscribes to WebSocket topic, then makes the export request
 */
export const useExport = (): UseExportReturn => {
  const [loadingExport, setLoadingExport] = useState<
    Record<ExportEntityType, boolean>
  >({
    'work-orders': false,
    assets: false,
    locations: false,
    parts: false,
    meters: false,
    'preventive-maintenances': false,
    'costs-times': false,
    'part-transactions': false
  });
  const [stompClient, setStompClient] = useState<SessionSocket | null>(null);
  const { user } = useAuth();
  useEffect(() => {
    if (!user?.email) { setStompClient(null); return; }
    const socket = createSessionSocket(user.email);
    setStompClient(socket);
    return () => socket.close();
  }, [user?.id, user?.email]);

  const exportEntity = useCallback(
    async (entity: ExportEntityType): Promise<void> => {
      return new Promise((resolve, reject) => {
        // Check if stompClient is initialized
        if (!stompClient?.connected) {
          reject(new Error('WebSocket connection not initialized'));
          return;
        }

        // Generate UUID client-side
        const uuid = crypto.randomUUID();

        let timeout: ReturnType<typeof setTimeout>;
        let subscription: { unsubscribe: () => void };
        const cancelled = (error: Error) => {
          clearTimeout(timeout);
          setLoadingExport((prev) => ({ ...prev, [entity]: false }));
          reject(error);
        };
        // Subscribe to WebSocket topic before making request
        subscription = stompClient.subscribe(
          `/user/${user.email}/exports/${uuid}`,
          function (message) {
            try {
              const url = message.body;
              if (url.includes('error:')) reject();
              else {
                window.open(url, '_blank');
                resolve();
              }
            } catch (error) {
              reject(error);
            } finally {
              clearTimeout(timeout);
              subscription.unsubscribe();
              setLoadingExport((prev) => ({ ...prev, [entity]: false }));
            }
          },
          cancelled
        );
        timeout = setTimeout(() => {
          subscription.unsubscribe();
          cancelled(new Error('Operation result timed out; check its status before retrying'));
        }, 300000);
        // Handle subscription error
        if (!subscription) {
          reject(new Error('Failed to subscribe to WebSocket topic'));
          setLoadingExport((prev) => ({ ...prev, [entity]: false }));
          return;
        }

        // Make request with UUID
        setLoadingExport((prev) => ({ ...prev, [entity]: true }));
        api
          .get<{ success: boolean; message: string }>(
            `export/${entity}?uuid=${uuid}`
          )
          .catch((error) => {
            clearTimeout(timeout);
            subscription.unsubscribe();
            setLoadingExport((prev) => ({ ...prev, [entity]: false }));
            reject(error);
          });
      });
    },
    [stompClient, user?.email]
  );

  return {
    exportEntity,
    loadingExport
  };
};

export default useExport;
