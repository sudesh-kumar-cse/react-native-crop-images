#import "CropImage.h"

@implementation CropImage

RCT_EXPORT_MODULE();

- (dispatch_queue_t)methodQueue {
  NSLog(@"methodQueue: Using main dispatch queue.");
  return dispatch_get_main_queue();
}

RCT_EXPORT_METHOD(pickImage:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  NSLog(@"pickImage: Initiating image pick.");
  self.resolve = resolve;
  self.reject = reject;

  self.imagePickerController = [[UIImagePickerController alloc] init];
  self.imagePickerController.delegate = self;
  self.imagePickerController.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;

  [self presentImagePickerController];
}

RCT_EXPORT_METHOD(captureImage:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  NSLog(@"captureImage: Initiating image capture.");
  self.resolve = resolve;
  self.reject = reject;

  self.imagePickerController = [[UIImagePickerController alloc] init];
  self.imagePickerController.delegate = self;
  self.imagePickerController.sourceType = UIImagePickerControllerSourceTypeCamera;

  [self presentImagePickerController];
}

RCT_EXPORT_METHOD(configure:(NSDictionary *)options) {
  NSLog(@"configure: Setting crop options with provided parameters.");
  self.options = options;
  self.cropType = options[@"cropType"];
  self.freeStyleCropEnabled = [options[@"freeStyleCropEnabled"] boolValue];
  self.showCropFrame = [options[@"showCropFrame"] boolValue];
  self.showCropGrid = [options[@"showCropGrid"] boolValue];
  self.dimmedLayerColor = [self colorWithHexString:options[@"dimmedLayerColor"]];
}

- (void)presentImagePickerController {
  NSLog(@"presentImagePickerController: Attempting to present image picker.");
  UIViewController *rootViewController = [UIApplication sharedApplication].delegate.window.rootViewController;
  
  if (rootViewController.presentedViewController) {
    NSLog(@"Warning: A modal is already being presented. Dismissing current modal before presenting new one.");
    [rootViewController.presentedViewController dismissViewControllerAnimated:NO completion:^{
      [rootViewController presentViewController:self.imagePickerController animated:YES completion:^{
        NSLog(@"Image Picker presented successfully.");
      }];
    }];
  } else {
    [rootViewController presentViewController:self.imagePickerController animated:YES completion:^{
      NSLog(@"Image Picker presented successfully.");
    }];
  }
}

- (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary<NSString *,id> *)info {
  NSLog(@"imagePickerController: Image picked, processing crop presentation.");
  UIImage *selectedImage = info[UIImagePickerControllerOriginalImage];

  [picker dismissViewControllerAnimated:YES completion:^{
    [self presentCropViewControllerWithImage:selectedImage];
  }];
}

- (void)imagePickerControllerDidCancel:(UIImagePickerController *)picker {
  NSLog(@"imagePickerControllerDidCancel: User cancelled image picking.");
  [picker dismissViewControllerAnimated:YES completion:^{
    self.reject(@"ERROR", @"Image picker was cancelled", nil);
  }];
}

- (void)presentCropViewControllerWithImage:(UIImage *)image {
  NSLog(@"presentCropViewControllerWithImage: Presenting crop view controller.");
  TOCropViewController *cropViewController;

  if ([self.cropType isEqualToString:@"circular"]) {
    cropViewController = [[TOCropViewController alloc] initWithCroppingStyle:TOCropViewCroppingStyleCircular image:image];
    NSLog(@"Crop Style: Circular");
  } else {
    cropViewController = [[TOCropViewController alloc] initWithImage:image];
    NSLog(@"Crop Style: Default");
  }

  cropViewController.delegate = self;
  cropViewController.aspectRatioLockEnabled = NO;
  cropViewController.resetAspectRatioEnabled = NO;

  if (self.freeStyleCropEnabled) {
    cropViewController.aspectRatioPickerButtonHidden = NO;
    cropViewController.resetAspectRatioEnabled = YES;
    NSLog(@"Free style crop enabled.");
  }

  cropViewController.cropView.cropBoxResizeEnabled = self.showCropFrame;
  cropViewController.cropView.gridOverlayHidden = !self.showCropGrid;
  cropViewController.cropView.backgroundColor = self.dimmedLayerColor;

  UIViewController *rootViewController = [UIApplication sharedApplication].delegate.window.rootViewController;
  [rootViewController presentViewController:cropViewController animated:YES completion:^{
    NSLog(@"Crop View Controller presented successfully.");
  }];
}

- (void)cropViewController:(TOCropViewController *)cropViewController didCropToImage:(UIImage *)image withRect:(CGRect)cropRect angle:(NSInteger)angle {
  NSLog(@"cropViewController: Image cropped.");
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
  NSLog(@"cropViewController: Circular image cropped.");
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
  NSLog(@"imageByMakingBackgroundTransparent: Making image background transparent.");
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
  NSLog(@"saveImage: Saving cropped image to file system.");
  NSData *imageData = UIImagePNGRepresentation(image);
  NSString *documentsPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) firstObject];
  NSString *filePath = [documentsPath stringByAppendingPathComponent:[NSString stringWithFormat:@"cropped_%@.png", [[NSUUID UUID] UUIDString]]];

  if ([imageData writeToFile:filePath atomically:YES]) {
    NSLog(@"Image saved successfully at path: %@", filePath);
    return filePath;
  } else {
    NSLog(@"Error: Failed to save image.");
    return nil;
  }
}

- (UIColor *)colorWithHexString:(NSString *)hexString {
    if (hexString.length == 0) {
        NSLog(@"colorWithHexString: Hex string is empty, using default color.");
        return [UIColor blackColor];
    }

    unsigned rgbValue = 0;
    NSScanner *scanner = [NSScanner scannerWithString:hexString];
    
    if ([hexString hasPrefix:@"#"]) {
        [scanner setScanLocation:1];
    }

    [scanner scanHexInt:&rgbValue];
    NSLog(@"colorWithHexString: Hex color parsed as RGB: #%06x", rgbValue);

    return [UIColor colorWithRed:((rgbValue & 0xFF0000) >> 16) / 255.0
                           green:((rgbValue & 0x00FF00) >> 8) / 255.0
                            blue:(rgbValue & 0x0000FF) / 255.0
                           alpha:1.0];
}

@end
