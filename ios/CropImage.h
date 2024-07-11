#import <React/RCTBridgeModule.h>
#import <React/RCTUtils.h>
#import <UIKit/UIKit.h>
#import "TOCropViewController.h"

@interface ImagePickerModule : NSObject <RCTBridgeModule, UIImagePickerControllerDelegate, UINavigationControllerDelegate, TOCropViewControllerDelegate>

@property (nonatomic, strong) UIImagePickerController *imagePickerController;
@property (nonatomic, copy) RCTPromiseResolveBlock resolve;
@property (nonatomic, copy) RCTPromiseRejectBlock reject;
@property (nonatomic, strong) NSDictionary *options;
@property (nonatomic, assign) BOOL freeStyleCropEnabled;
@property (nonatomic, assign) BOOL showCropFrame;
@property (nonatomic, assign) BOOL showCropGrid;
@property (nonatomic, strong) UIColor *dimmedLayerColor;
@property (nonatomic, strong) NSString *cropType;

@end
