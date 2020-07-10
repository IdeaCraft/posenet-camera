#import "CameraInferenceViewPlugin.h"
#if __has_include(<cameraInferenceView/cameraInferenceView-Swift.h>)
#import <cameraInferenceView/cameraInferenceView-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "cameraInferenceView-Swift.h"
#endif

@implementation CameraInferenceViewPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftCameraInferenceViewPlugin registerWithRegistrar:registrar];
}
@end
