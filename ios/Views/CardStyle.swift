//
//  CardStyle.swift
//  TelemetryApp
//
//  Created by Alex on 17.03.26.
//

import SwiftUI

struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(12)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color(.separator).opacity(0.25), lineWidth: 1)
            )
    }
}

extension View {
    func cardStyle() -> some View {
        self.modifier(CardStyle())
    }
}
