
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNCropImageSpec.h"

@interface CropImage : NSObject <NativeCropImageSpec>
#else
#import <React/RCTBridgeModule.h>

@interface CropImage : NSObject <RCTBridgeModule>
#endif

@end
