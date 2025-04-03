import UIKit

@available(iOS 13.0, *)
@objc(DocumentScanner)
class DocumentScanner: NSObject {

    @objc static func requiresMainQueueSetup() -> Bool {
        return true
    }
    
    /** @property  documentScanner the document scanner */
    private var documentScanner: DocScanner?

    @objc func convertBase64ImagesToGrayscale(_ base64Images: [String]) -> [String] {
        var grayscaleBase64Images: [String] = []

        for base64String in base64Images {
            if let imageData = Data(base64Encoded: base64String),
            let image = UIImage(data: imageData),
            let grayscaleImage = convertToGrayscale(image),
            let grayscaleImageData = grayscaleImage.jpegData(compressionQuality: 1.0) {
                
                let grayscaleBase64String = grayscaleImageData.base64EncodedString()
                grayscaleBase64Images.append(grayscaleBase64String)
            }
        }
        
        return grayscaleBase64Images
    }

    @objc func convertToGrayscale(_ image: UIImage) -> UIImage? {
        let ciImage = CIImage(image: image)
        let grayscaleFilter = CIFilter(name: "CIPhotoEffectMono")
        grayscaleFilter?.setValue(ciImage, forKey: kCIInputImageKey)

        if let outputImage = grayscaleFilter?.outputImage,
        let cgImage = CIContext().createCGImage(outputImage, from: outputImage.extent) {
            return UIImage(cgImage: cgImage)
        }
        
        return nil
    }

    @objc(scanDocument:withResolver:withRejecter:)
    func scanDocument(
      _ options: NSDictionary,
      resolve: @escaping RCTPromiseResolveBlock,
      reject: @escaping RCTPromiseRejectBlock
    ) -> Void {
        DispatchQueue.main.async {
            self.documentScanner = DocScanner()

            // launch the document scanner
            self.documentScanner?.startScan(
                RCTPresentedViewController(),
                successHandler: { (scannedDocumentImages: [String]) in
                    // document scan success
                    resolve([
                        "status": "success",
                        "scannedImages": self.convertBase64ImagesToGrayscale(scannedDocumentImages)
                    ])
                    self.documentScanner = nil
                },
                errorHandler: { (errorMessage: String) in
                    // document scan error
                    reject("document scan error", errorMessage, nil)
                    self.documentScanner = nil
                },
                cancelHandler: {
                    // when user cancels document scan
                    resolve([
                        "status": "cancel"
                    ])
                    self.documentScanner = nil
                },
                responseType: options["responseType"] as? String,
                croppedImageQuality: options["croppedImageQuality"] as? Int
            )
        }
    }

}
