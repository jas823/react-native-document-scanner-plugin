// index.d.ts

declare module '@jchua_dev/react-native-document-scanner-plugin' {
    // Define possible response types as string literal types
    export type ResponseType = 'base64' | 'uri' | 'data';
  
    // Options for scanning document
    export interface DocumentScannerOptions {
      responseType?: ResponseType;  // The type of response expected
      croppedImageQuality?: number; // Quality of the cropped image (0 to 100)
      maxNumDocuments?: number;    // Maximum number of documents to scan
    }
  
    // Result of scanning the document
    export interface DocumentScanResult {
      status: 'success' | 'cancel' | 'error'; // Status of the scan
      scannedImages?: string[];  // Array of base64-encoded images
    }
  
    // Main class for DocumentScanner
    export default class DocumentScanner {
      // Method to check if it requires main queue setup
      static requiresMainQueueSetup(): boolean;
  
      // Method to convert an array of base64 image strings to grayscale
      static convertBase64ImagesToGrayscale(base64Images: string[]): string[];
  
      // Method to start the document scanning process
      static scanDocument(
        options: DocumentScannerOptions,  // Options for the scan
      ): Promise<DocumentScanResult>;
    }
  }
  