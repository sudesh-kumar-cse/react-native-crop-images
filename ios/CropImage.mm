#import "CropImage.h"
#import <PhotosUI/PhotosUI.h>

@implementation CropImage

RCT_EXPORT_MODULE();

- (dispatch_queue_t)methodQueue {
  NSLog(@"methodQueue: Using main dispatch queue.");
  return dispatch_get_main_queue();
}

- (void)presentViewController:(UIViewController *)viewController {
  UIViewController *rootViewController = [UIApplication sharedApplication].delegate.window.rootViewController;
  if (rootViewController.presentedViewController) {
    [rootViewController.presentedViewController dismissViewControllerAnimated:NO completion:^{
      [rootViewController presentViewController:viewController animated:YES completion:nil];
    }];
  } else {
    [rootViewController presentViewController:viewController animated:YES completion:nil];
  }
}

RCT_EXPORT_METHOD(pickImage:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  NSLog(@"pickImage: Initiating image pick.");
  self.resolve = resolve;
  self.reject = reject;

  if ([self.options[@"multipleImage"] boolValue]) {
    // Use PHPickerViewController for multiple selection
    PHPickerConfiguration *config = [[PHPickerConfiguration alloc] init];
    config.selectionLimit = 0; // 0 means no limit
    config.filter = [PHPickerFilter imagesFilter];
    
    PHPickerViewController *pickerViewController = [[PHPickerViewController alloc] initWithConfiguration:config];
    pickerViewController.delegate = self;
    
    [self presentViewController:pickerViewController];
  } else {
    // Use existing UIImagePickerController for single selection
    self.imagePickerController = [[UIImagePickerController alloc] init];
    self.imagePickerController.delegate = self;
    self.imagePickerController.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;
    
    [self presentImagePickerController];
  }
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
  NSLog(@"configure: Setting options with provided parameters.");
  self.options = options;
  self.cropType = options[@"cropType"];
  self.cropEnabled = [options[@"cropEnabled"] boolValue];
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
  NSLog(@"imagePickerController: Image picked, processing.");
  
  UIImage *selectedImage = info[UIImagePickerControllerOriginalImage];
  [picker dismissViewControllerAnimated:YES completion:^{
    // Check if cropping is enabled
    if (!self.cropEnabled) {
      NSLog(@"Cropping disabled, processing original image.");
      NSString *imagePath;
      
      // Check cropType to determine how to process the image
      if ([self.cropType isEqualToString:@"circular"]) {
        // Create a circular image
        CGSize imageSize = selectedImage.size;
        CGFloat sideLength = MIN(imageSize.width, imageSize.height); // Use the smaller dimension for a square
        CGRect circularRect = CGRectMake((imageSize.width - sideLength) / 2, (imageSize.height - sideLength) / 2, sideLength, sideLength);
        
        UIImage *circularImage = [self createCircularImageWithImage:selectedImage inRect:circularRect];
        imagePath = [self saveImage:circularImage];
      } else {
        // Save the original image normally
        imagePath = [self saveImage:selectedImage];
      }
      
      if (imagePath) {
        NSLog(@"pickImage: Selected image path: %@", imagePath);
        
        // Wrap the response in an array format
        [self sendImageResponse:imagePath]; // Call the method to send response
      } else {
        self.reject(@"ERROR", @"Failed to save original image", nil);
      }
      return;
    }
    
    // If cropping is enabled, proceed with normal flow
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
  NSLog(@"cropViewController: Rectangle image cropped. Image size: %@", NSStringFromCGSize(image.size));
  [cropViewController dismissViewControllerAnimated:YES completion:^{
    // Save the cropped rectangle image
    NSString *croppedImagePath = [self saveImage:image]; 
    if (croppedImagePath) {
      NSLog(@"Cropped rectangle image saved at path: %@", croppedImagePath);
      // Send the response in a consistent format
      [self sendImageResponse:croppedImagePath]; // Use the same response method
    } else {
      NSLog(@"Error: Failed to save cropped rectangle image.");
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
      [self sendImageResponse:croppedImagePath];
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
  NSLog(@"saveImage: Saving image to file system.");
  
  // Convert image to PNG format
  NSData *imageData = UIImagePNGRepresentation(image);
  
  // If PNG conversion fails, try JPEG as fallback
  if (!imageData) {
    NSLog(@"Warning: PNG conversion failed, trying JPEG...");
    imageData = UIImageJPEGRepresentation(image, 0.8); // Adjust quality as needed
  }
  
  if (!imageData) {
    NSLog(@"Error: Failed to convert image to either PNG or JPEG format.");
    return nil;
  }
  
  NSLog(@"Image data size: %lu bytes", (unsigned long)imageData.length); // Log image data size
  
  NSString *documentsPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) firstObject];
  NSString *fileName = [NSString stringWithFormat:@"image_%@.png", [[NSUUID UUID] UUIDString]];
  NSString *filePath = [documentsPath stringByAppendingPathComponent:fileName];

  NSError *error = nil;
  BOOL success = [imageData writeToFile:filePath options:NSDataWritingAtomic error:&error];
  
  if (success) {
    NSLog(@"Image saved successfully at path: %@", filePath);
    return filePath;
  } else {
    NSLog(@"Error: Failed to save image. Error: %@", error.localizedDescription);
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

- (UIImage *)createCircularImageWithImage:(UIImage *)image inRect:(CGRect)rect {
  NSLog(@"createCircularImageWithImage: Creating circular image.");
  UIGraphicsBeginImageContextWithOptions(rect.size, NO, image.scale);
  
  // Create a circular path
  UIBezierPath *circularPath = [UIBezierPath bezierPathWithOvalInRect:CGRectMake(0, 0, rect.size.width, rect.size.height)];
  [circularPath addClip];
  
  // Calculate the drawing rect to center the image
  CGFloat xOffset = (rect.size.width - image.size.width) / 2.0;
  CGFloat yOffset = (rect.size.height - image.size.height) / 2.0;
  
  // Draw the image in the circular path
  [image drawInRect:CGRectMake(xOffset, yOffset, image.size.width, image.size.height)];
  
  UIImage *circularImage = UIGraphicsGetImageFromCurrentImageContext();
  UIGraphicsEndImageContext();
  
  return circularImage; // Return the circular image
}

- (void)picker:(PHPickerViewController *)picker didFinishPicking:(NSArray<PHPickerResult *> *)results {
  [picker dismissViewControllerAnimated:YES completion:nil];
  
  if (results.count == 0) {
    self.reject(@"ERROR", @"No images selected", nil);
    return;
  }
  
  NSMutableArray *imagePaths = [NSMutableArray array];
  dispatch_group_t group = dispatch_group_create();
  
  for (PHPickerResult *result in results) {
    dispatch_group_enter(group);
    [result.itemProvider loadObjectOfClass:[UIImage class] completionHandler:^(__kindof id<NSItemProviderReading>  _Nullable object, NSError * _Nullable error) {
      if ([object isKindOfClass:[UIImage class]]) {
        UIImage *image = (UIImage *)object;
        NSString *imagePath = [self saveImage:image];
        if (imagePath) {
          [imagePaths addObject:imagePath];
        }
      }
      dispatch_group_leave(group);
    }];
  }
  
  dispatch_group_notify(group, dispatch_get_main_queue(), ^{
    if (imagePaths.count > 0) {
     [self sendImageResponseWithPaths:imagePaths];
    } else {
      self.reject(@"ERROR", @"Failed to save images", nil);
    }
  });
  
}

- (void)sendImageResponseWithPaths:(NSArray<NSString *> *)imagePaths {
    NSLog(@"sendImageResponseWithPaths: Formatting image response for multiple images.");

    NSMutableArray *images = [NSMutableArray array];
    NSTimeInterval timestamp = [[NSDate date] timeIntervalSince1970] * 1000; // Current timestamp in milliseconds

    for (NSInteger i = 0; i < imagePaths.count; i++) {
        NSString *imagePath = imagePaths[i];
        // Simulated metadata for each image
        NSDictionary *imageInfo = @{
            @"timestamp": @(timestamp),
            @"fileName": [imagePath lastPathComponent],
            @"type": @"image/jpeg",
            @"index": @(i),
            @"size": @(0.09630203247070312),
            @"height": @(1280),
            @"width": @(960),
            @"uri": [NSString stringWithFormat:@"file://%@", imagePath]
        };
        [images addObject:imageInfo];
    }

    // Build the response JSON
    NSDictionary *response = @{
        @"hasErrors": @NO,
        @"count": @(images.count),
        @"multiple": @YES,
        @"response": @{
            @"images": images
        }
    };

    self.resolve(response); // Send the formatted response back to JavaScript
}

- (void)sendImageResponse:(NSString *)imagePath {
    NSLog(@"sendImageResponse: Sending image response.");
    
    // Ensure the file path starts with 'file://'
    NSString *filePath = [NSString stringWithFormat:@"file://%@", imagePath];
    
    // Simulate image metadata
    NSDictionary *imageInfo = @{
        @"timestamp": @([[NSDate date] timeIntervalSince1970] * 1000),
        @"fileName": [imagePath lastPathComponent],
        @"type": @"image/jpeg",
        @"index": @(0),
        @"size": @(0.09630203247070312),
        @"height": @(1280),
        @"width": @(960),
        @"uri": filePath,
        @"imageQuality": @(self.options[@"imageQuality"] ? [self.options[@"imageQuality"] floatValue] : 100),
        @"maxFileSize": @(self.options[@"maxFileSize"] ? [self.options[@"maxFileSize"] floatValue] : 0)
    };

    // Wrap the single image in a similar structure as multiple images
    NSDictionary *response = @{
        @"hasErrors": @NO,
        @"count": @1,
        @"multiple": @NO,
        @"response": @{
            @"images": @[imageInfo] // Ensure single image is always wrapped in an array
        }
    };
    
    self.resolve(response);
}

@end
