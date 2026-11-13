import { useState, useEffect, useCallback } from 'react';
import { createSessionSocket, SessionSocket } from '../utils/sessionSocket';
import api from 'src/utils/api';
import useAuth from './useAuth';

interface UseImportReturn {
  importEntity: (entity: EntityType, payload: any[]) => Promise<ImportResponse>;
  loadingImport: boolean;
}

export type EntityType =
  | 'work-orders'
  | 'assets'
  | 'locations'
  | 'parts'
  | 'meters'
  | 'preventive-maintenances';

export interface ImportResponse {
  success: boolean;
  created: number;
  updated: number;
  message?: string;
}

/**
 * Custom hook for importing entities with WebSocket support
 * Generates a UUID, subscribes to WebSocket topic, then makes the import request
 */
export const useImport = (): UseImportReturn => {
  const [loadingImport, setLoadingImport] = useState<boolean>(false);
  const [stompClient, setStompClient] = useState<SessionSocket | null>(null);
  const { user } = useAuth();
  useEffect(() => {
    if (!user?.email) { setStompClient(null); return; }
    const socket = createSessionSocket(user.email);
    setStompClient(socket);
    return () => socket.close();
  }, [user?.id, user?.email]);

  const importEntity = useCallback(
    async (entity: EntityType, payload: any[]): Promise<ImportResponse> => {
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
          setLoadingImport(false);
          reject(error);
        };
        // Subscribe to WebSocket topic before making request
        subscription = stompClient.subscribe(
          `/user/${user.email}/imports/${uuid}`,
          function (message) {
            try {
              const response = JSON.parse(message.body);

              // Check if it's an error message
              if (typeof response === 'string' && response.includes('error:')) {
                const errorMessage =
                  response.split('error: ')[1] || 'Import failed';
                reject(new Error(errorMessage));
              } else {
                // It's a successful ImportResponse
                resolve(response as ImportResponse);
              }
            } catch (error) {
              // Handle non-JSON error messages
              if (
                typeof message.body === 'string' &&
                message.body.includes('error:')
              ) {
                const errorMessage =
                  message.body.split('error: ')[1] || 'Import failed';
                reject(new Error(errorMessage));
              } else {
                reject(error);
              }
            } finally {
              clearTimeout(timeout);
              subscription.unsubscribe();
              setLoadingImport(false);
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
          setLoadingImport(false);
          return;
        }

        // Make request with UUID
        setLoadingImport(true);
        api
          .post<ImportResponse>(`${basePath}/${entity}?uuid=${uuid}`, payload)
          .catch((error) => {
            clearTimeout(timeout);
            subscription.unsubscribe();
            setLoadingImport(false);
            reject(error);
          });
      });
    },
    [stompClient, user?.email]
  );

  return {
    importEntity,
    loadingImport
  };
};

const basePath = 'import';

export default useImport;
