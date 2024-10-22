#import "CropImage.h"

@implementation CropImage

RCT_EXPORT_MODULE();

- (dispatch_queue_t)methodQueue {
  return dispatch_get_main_queue();
}

RCT_EXPORT_METHOD(pickImage:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  self.resolve = resolve;
  self.reject = reject;

  self.imagePickerController = [[UIImagePickerController alloc] init];
  self.imagePickerController.delegate = self;
  self.imagePickerController.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;

  [self presentImagePickerController];
}

RCT_EXPORT_METHOD(captureImage:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  self.resolve = resolve;
  self.reject = reject;

  self.imagePickerController = [[UIImagePickerController alloc] init];
  self.imagePickerController.delegate = self;
  self.imagePickerController.sourceType = UIImagePickerControllerSourceTypeCamera;

  [self presentImagePickerController];
}

RCT_EXPORT_METHOD(configure:(NSDictionary *)options) {
  self.options = options;
  self.cropType = options[@"cropType"]; 
  self.freeStyleCropEnabled = [options[@"freeStyleCropEnabled"] boolValue];
  self.showCropFrame = [options[@"showCropFrame"] boolValue];
  self.showCropGrid = [options[@"showCropGrid"] boolValue];
  self.dimmedLayerColor = [self colorWithHexString:options[@"dimmedLayerColor"]];
}

- (void)presentImagePickerController {
  UIViewController *rootViewController = [UIApplication sharedApplication].delegate.window.rootViewController;
  [rootViewController presentViewController:self.imagePickerController animated:YES completion:nil];
}

- (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary<NSString *,id> *)info {
  UIImage *selectedImage = info[UIImagePickerControllerOriginalImage];

  [picker dismissViewControllerAnimated:YES completion:^{
    [self presentCropViewControllerWithImage:selectedImage];
  }];
}

- (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker {
  [picker dismissViewControllerAnimated:YES completion:^{
    self.reject(@"ERROR", @"Image picker was cancelled", nil);
  }];
}

- (void)presentCropViewControllerWithImage:(UIImage *)image {
  TOCropViewController *cropViewController;

  if ([self.cropType isEqualToString:@"circular"]) {
    cropViewController = [[TOCropViewController alloc] initWithCroppingStyle:TOCropViewCroppingStyleCircular image:image];
  } else {
    cropViewController = [[TOCropViewController alloc] initWithImage:image];
  }

  cropViewController.delegate = self;
  cropViewController.aspectRatioLockEnabled = NO;
  cropViewController.resetAspectRatioEnabled = NO;

  if (self.freeStyleCropEnabled) {
    cropViewController.aspectRatioPickerButtonHidden = NO;
    cropViewController.resetAspectRatioEnabled = YES;
  }

  cropViewController.cropView.cropBoxResizeEnabled = self.showCropFrame;
  cropViewController.cropView.gridOverlayHidden = !self.showCropGrid;
  cropViewController.cropView.backgroundColor = self.dimmedLayerColor;

  UIViewController *rootViewController = [UIApplication sharedApplication].delegate.window.rootViewController;
  [rootViewController presentViewController:cropViewController animated:YES completion:nil];
}

- (void)cropViewController:(TOCropViewController *)cropViewController didCropToImage:(UIImage *)image withRect:(CGRect)cropRect angle:(NSInteger)angle {
  [cropViewController dismissViewControllerAnimated:YES completion:^{
    NSString *croppedImagePath = [self saveImage:image];
    if (croppedImagePath) {
      self.resolve(croppedImagePath);
    } else {
      self.reject(@"ERROR", @"Failed to save cropped image", nil);
    }
  }];
}

- (void)cropViewController:(TOCropViewController *)cropViewController didCropToCircularImage:(UIImage *)image withRect:(CGRect)cropRect angle:(NSInteger)angle {
  [cropViewController dismissViewControllerAnimated:YES completion:^{
    UIImage *transparentImage = [self imageByMakingBackgroundTransparent:image];
    NSString *croppedImagePath = [self saveImage:transparentImage];
    if (croppedImagePath) {
      self.resolve(croppedImagePath);
    } else {
      self.reject(@"ERROR", @"Failed to save cropped image", nil);
    }
  }];
}

- (UIImage *)imageByMakingBackgroundTransparent:(UIImage *)image {
  CGRect rect = CGRectMake(0, 0, image.size.width, image.size.height);
  UIGraphicsBeginImageContextWithOptions(image.size, NO, image.scale);
  [[UIColor clearColor] setFill];
  UIRectFill(rect);
  [image drawInRect:rect];
  UIImage *transparentImage = UIGraphicsGetImageFromCurrentImageContext();
  UIGraphicsEndImageContext();
  return transparentImage;
}

- (NSString *)saveImage:(UIImage *)image {
  NSData *imageData = UIImagePNGRepresentation(image);
  NSString *documentsPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) firstObject];
  NSString *filePath = [documentsPath stringByAppendingPathComponent:[NSString stringWithFormat:@"cropped_%@.png", [[NSUUID UUID] UUIDString]]];

  if ([imageData writeToFile:filePath atomically:YES]) {
    return filePath;
  } else {
    return nil;
  }
}

- (UIColor *)colorWithHexString:(NSString *)hexString {
  unsigned rgbValue = 0;
  NSScanner *scanner = [NSScanner scannerWithString:hexString];
  [scanner setScanLocation:1]; // bypass '#' character
  [scanner scanHexInt:&rgbValue];
  return [UIColor colorWithRed:((rgbValue & 0xFF0000) >> 16) / 255.0 green:((rgbValue & 0x00FF00) >> 8) / 255.0 blue:(rgbValue & 0x0000FF) / 255.0 alpha:1.0];
}

@end
