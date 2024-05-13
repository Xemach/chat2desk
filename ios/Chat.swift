import Foundation
import chat2desk_sdk


@objc(Chat)
class Chat: RCTEventEmitter {
  
  var messagesWatcher: Closeable?
  var connectedWatcher: Closeable?
  
  var chat2desk: Chat2Desk?
  

  override init() {
      super.init()
  }

  init(chat2desk: Chat2Desk) {
    self.chat2desk = chat2desk
    super.init()
    
  }

  @objc(initChat:resolver:rejecter:)
  func initChat(_ params: NSDictionary, _ resolve: @escaping RCTPromiseResolveBlock,
            rejecter reject: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.main.async {
      Task {
        do {
          let settings = Settings.init(
            authToken: params["token"] as? String ?? "",
            baseHost: params["baseHost"] as? String ?? "",
            wsHost: params["wsHost"] as? String ?? "",
            storageHost: params["storageHost"] as? String ?? ""
          )
          settings.withLog = true
          settings.logLevel = Ktor_client_loggingLogLevel.info
          self.chat2desk = Chat2Desk.Companion().create(settings: settings)
          resolve("Success")
        } catch {
          reject("ERROR", "not init chat", error)
        }
      }
    }
  }
  
  
  @objc
  func getMessages() {
    messagesWatcher = chat2desk?.watchMessages().watch { messages in
      let rnMessages = messages?.compactMap({ $0 as? Message }).map({ message in
        let attachments = message.attachments?.map({ attachment in
                // Convert attachment to dictionary format
                return [
                  "id": attachment.id,
                  "originalFileName": attachment.originalFileName,
                  "link": attachment.link,
                  "contentType": attachment.contentType
                ]
            })
        return ["id": message.id, "text": message.text, "date": message.date?.toEpochMilliseconds(), "status": message.status.name, "type": message.type.name, "attachments": attachments] }) ?? []
      self.sendEvent(withName: "onMessage", body: ["messages": rnMessages])
    }
  }
  
  
  @objc
  func checkIsConnected() {
    connectedWatcher = chat2desk?.watchConnectionStatus().watch() { status in
      self.sendEvent(withName: "connection_status", body: ["connection": status?.name])
    }
  }
  
  @objc(sendUserInfo:resolver:rejecter:)
  func sendUserInfo(_ params: NSDictionary, _ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
    DispatchQueue.main.async {
      Task {
        do {
          try await self.chat2desk?.sendClientParams(name: params["name"] as? String ?? "", phone: params["phone"] as? String ?? "" , fieldSet: [:])
          resolve("Success send")
        } catch {
          reject("Error start", "dont start chat", error)
        }
      }
    }
  }
  
  
  @objc
  func fetchNewMessages(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
    DispatchQueue.main.async {
      Task {
        do {
          try await self.chat2desk?.fetchNewMessages()
          resolve("Success fetch")
        } catch {
          reject("Error start", "dont start chat", error)
        }
      }
    }
  }
  
  @objc(sendMessage:resolver:rejecter:)
  func sendMessage(_ text: String, _ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
    DispatchQueue.main.async {
      Task {
        do {
          try await self.chat2desk?.sendMessage(msg: text)
          resolve("Success send")
        } catch {
          reject("Error start", "dont send text chat", error)
        }
      }
    }
  }
  
  @objc(sendFile:resolver:rejecter:)
  func sendFile(_ file: NSDictionary, _ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
    DispatchQueue.main.async {
      Task {
        do {
          let attachedFile = AttachedFile.Companion().fromURL(url: URL(string: file["uri"] as! String)!, originalName: file["originalName"] as! String, mimeType: file["mimeType"] as! String, fileSize: file["fileSize"] as! Int32)
          print(file, "FILE")
          print(attachedFile.debugDescription, "ATTACHEDFILE")
          try await self.chat2desk?.sendMessage(msg: "", attachedFile: attachedFile!)
          resolve("Success send")
        } catch {
          reject("Error start", "don't send file chat", error)
        }
      }
    }
    
  }
  
  @objc
  func openChat(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
    DispatchQueue.main.async {
      Task {
        do {
          try await self.chat2desk?.start()
          self.getMessages()
          self.checkIsConnected()
          resolve("Success start")
        } catch {
          reject("Error start", "dont start chat", error)
        }
      }
    }
  }
  
  
  @objc
  func closeChat(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
    DispatchQueue.main.async {
      Task {
        do {
          try await self.chat2desk?.stop()
          self.messagesWatcher?.close()
          resolve("Success stop")
        } catch {
          reject("ERROR", "ERROR STOP", error)
        }
      }
    }
  }
  
  @objc
  func destroyChat(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) -> Void {
    DispatchQueue.main.async {
      Task {
        do {
          self.chat2desk?.close()
          resolve("Success destroy")
        } catch {
          reject("ERROR", "ERROR DESTROY", error)
        }
      }
    }
  }
  
  
  
  deinit {
    chat2desk?.close()
    messagesWatcher?.close()
  }
  
  
  override func supportedEvents() -> [String]! {
      return ["onMessage", "connection_status"]
  }
  

  
  
}
