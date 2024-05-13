
import { NativeModules } from 'react-native';

const { Chat2Desk: Chat2DeskRN } = NativeModules;

interface SettingsChat2Desk {
    token: string;
    baseHost: string;
    wsHost: string;
    storageHost: string;
}

export enum MESSAGE_TYPE {
    IN = 'IN',
    OUT = 'OUT'
}

export interface Chat2DeskMessages {
    messages: Chat2DeskMessage[]
}

export enum MESSAGE_STATUS {
    SENDING = 'SENDING',
    SENT = 'SENT',
    DELIVERED = 'DELIVERED',
    NOT_DELIVERED = 'NOT_DELIVERED'
}

export enum MESSAGE_READ {
    UNREAD = 'UNREAD',
    READ = 'READ'
}

export interface AttachmentFile {
    contentType: string;
    fileSize: number;
    id: number;
    link: string;
    originalFileName: string;
    status: MESSAGE_STATUS
}

export interface Chat2DeskMessage {
    attachments: AttachmentFile[],
    date: string,
    id: string,
    read: string,
    realId: string,
    status: string,
    text: string,
    type: MESSAGE_TYPE
}

export interface Chat2DeskFile {
    uri: string;
    originalName: string;
    mimeType: string;
    fileSize: number;
}

interface Chat2DeskType {
    initChat: (params: SettingsChat2Desk) => void;
    openChat(): Promise<void>;
    closeChat(): Promise<void>;
    sendMessage(text: string): Promise<void>;
    sendUserInfo(userInfo: { name: string; phone: string }): Promise<void>;
    fetchNewMessages(): Promise<void>;
    destroyChat(): Promise<void>;
    sendFile(file: Chat2DeskFile): Promise<void>;
}
export const Chat2Desk = Chat2DeskRN as Chat2DeskType;

